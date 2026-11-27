// Makes the Wi-Fi join QR code for TV Home with macOS's built-in CoreImage generator,
// then decodes it again to prove it reads back exactly.
// usage: swift make_wifi_qr.swift wifi.conf out.png
import CoreImage
import Foundation

let args = CommandLine.arguments
guard args.count == 3 else { print("usage: make_wifi_qr.swift wifi.conf out.png"); exit(2) }

var conf: [String: String] = [:]
for line in try String(contentsOfFile: args[1], encoding: .utf8).split(separator: "\n") {
    let t = line.trimmingCharacters(in: .whitespaces)
    if t.isEmpty || t.hasPrefix("#") { continue }
    if let eq = t.firstIndex(of: "=") {
        conf[String(t[..<eq])] = String(t[t.index(after: eq)...])
    }
}
guard let ssid = conf["SSID"], !ssid.isEmpty else { print("wifi.conf has no SSID"); exit(1) }
let password = conf["PASSWORD"] ?? ""
let security = conf["SECURITY"] ?? "WPA"

// Wi-Fi QR format; \ ; , : " must be backslash-escaped.
func esc(_ s: String) -> String {
    var out = ""
    for c in s { if "\\;,:\"".contains(c) { out.append("\\") }; out.append(c) }
    return out
}
let payload = security == "nopass"
    ? "WIFI:T:nopass;S:\(esc(ssid));;"
    : "WIFI:T:\(security);S:\(esc(ssid));P:\(esc(password));;"

let gen = CIFilter(name: "CIQRCodeGenerator")!
gen.setValue(payload.data(using: .utf8), forKey: "inputMessage")
gen.setValue("M", forKey: "inputCorrectionLevel")
let qr = gen.outputImage!

// Crisp integer scaling to ~640 px, plus a 4-module white quiet zone.
let scale = max(1, (640 / qr.extent.width).rounded(.down))
let quiet = 4 * scale
let scaled = qr.samplingNearest().transformed(by: CGAffineTransform(scaleX: scale, y: scale))
let side = scaled.extent.width + 2 * quiet
let canvas = CIImage(color: CIColor.white).cropped(to: CGRect(x: 0, y: 0, width: side, height: side))
let image = scaled.transformed(by: CGAffineTransform(translationX: quiet, y: quiet)).composited(over: canvas)

let ctx = CIContext()
let png = ctx.pngRepresentation(of: image, format: .RGBA8, colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!)!
try png.write(to: URL(fileURLWithPath: args[2]))

let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: ctx, options: nil)!
let decoded = (detector.features(in: CIImage(data: png)!) as? [CIQRCodeFeature])?.first?.messageString
guard decoded == payload else { print("QR self-check FAILED"); exit(1) }
print("QR ok: \(Int(side)) px, network \(ssid), decodes back exactly")
