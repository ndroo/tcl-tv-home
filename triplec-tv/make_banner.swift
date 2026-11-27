// Makes the Android TV banner (640x360) for Triple C: the logo in white on the brand red (#ff4d3d),
// like the app icon, so it sits with the other solid-colour app tiles on TV Home.
// Built from the media kit's black-on-transparent logo (mark in its red square, black wordmark).
// usage: swift make_banner.swift ~/tripleC/web/public/media-kit/triplec-logo-black-transparent.png res/drawable-xhdpi/banner.png
import CoreImage
import Foundation

let args = CommandLine.arguments
guard args.count == 3, let logo = CIImage(contentsOf: URL(fileURLWithPath: args[1])) else {
    print("usage: make_banner.swift logo-black-transparent.png out.png"); exit(2)
}
let W: CGFloat = 640, H: CGFloat = 360
let red = CIColor(red: 1.0, green: 0x4d / 255.0, blue: 0x3d / 255.0)
let ext = logo.extent                       // 1416 x 512; the red square ends before x = 470
let split = ext.width * 470 / 1416

// The mark: white where the square is white. Its green channel is 1 for white and ~0.3 for the red, so
// alpha = (G - 0.3) / 0.7 keeps just the rings and the leaf, anti-aliased.
let markPart = logo.cropped(to: CGRect(x: 0, y: 0, width: split, height: ext.height))
let mark = markPart.applyingFilter("CIColorMatrix", parameters: [
    "inputRVector": CIVector(x: 0, y: 0, z: 0, w: 0),
    "inputGVector": CIVector(x: 0, y: 0, z: 0, w: 0),
    "inputBVector": CIVector(x: 0, y: 0, z: 0, w: 0),
    "inputAVector": CIVector(x: 0, y: 1 / 0.7, z: 0, w: 0),
    "inputBiasVector": CIVector(x: 1, y: 1, z: 1, w: -0.3 / 0.7),
]).applyingFilter("CIColorClamp")

// The wordmark: its exact shape (alpha), filled white.
let wordPart = logo.cropped(to: CGRect(x: split, y: 0, width: ext.width - split, height: ext.height))
let white = CIImage(color: .white).cropped(to: wordPart.extent)
let word = white.applyingFilter("CIBlendWithAlphaMask", parameters: [
    kCIInputBackgroundImageKey: CIImage.empty(), kCIInputMaskImageKey: wordPart,
])

// Without its square the mark sits far from the wordmark, so it moves in by 60 px (of 1416). The drawn logo then
// spans x 240...1312 of the artwork; that span fills 64% of the banner, centred, on red (TV Home crops banners to its ~1.45:1 tiles).
let nudge = ext.width * 60 / 1416
let left = ext.width * 240 / 1416, right = ext.width * 1312 / 1416
let s = W * 0.64 / (right - left)
let placed = word.composited(over: mark.transformed(by: CGAffineTransform(translationX: nudge, y: 0)))
    .transformed(by: CGAffineTransform(scaleX: s, y: s))
    .transformed(by: CGAffineTransform(translationX: W / 2 - (left + right) / 2 * s, y: (H - ext.height * s) / 2))
let canvas = CIImage(color: red).cropped(to: CGRect(x: 0, y: 0, width: W, height: H))
let out = placed.composited(over: canvas).cropped(to: canvas.extent)

let ctx = CIContext()
let png = ctx.pngRepresentation(of: out, format: .RGBA8, colorSpace: CGColorSpace(name: CGColorSpace.sRGB)!)!
try png.write(to: URL(fileURLWithPath: args[2]))
print("banner: \(Int(W))x\(Int(H)) -> \(args[2])")
