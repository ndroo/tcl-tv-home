package local.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The TV's inputs as TV Home shows them (HDMI first, then AV, then the tuner), named by inputs.conf when it has a
 * name for one, and which are hidden (Settings -> Inputs on the home screen).
 */
final class Inputs {
    private Inputs() {}

    private static final String PREFS = "tvhome", KEY = "hidden_inputs";

    static final class Input {
        TvInputInfo info;
        String label;
        String detail;
        String port;   // the TV's name for it (HDMI 2), shown small when inputs.conf renames it
        int state;
    }

    /** Every input, hidden ones included. */
    static List<Input> all(Context ctx) {
        List<Input> out = new ArrayList<>();
        TvInputManager tvm = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tvm == null) return out;
        List<TvInputInfo> all;
        try {
            all = tvm.getTvInputList();
        } catch (RuntimeException e) {
            return out;
        }
        Map<String, String> names = new HashMap<>();   // inputs.conf, keys lower-case
        for (Map.Entry<String, String> e : Conf.read(ctx, "inputs.conf").entrySet()) {
            names.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
        }
        // HDMI-CEC devices show up as child inputs; use their names to describe the port.
        Map<String, String> cecNames = new HashMap<>();
        List<TvInputInfo> top = new ArrayList<>();
        for (TvInputInfo i : all) {
            if (i.getParentId() != null) {
                CharSequence l = i.loadLabel(ctx);
                if (!TextUtils.isEmpty(l)) cecNames.put(i.getParentId(), l.toString());
            } else if (!i.isHidden(ctx)) {
                top.add(i);
            }
        }
        Collections.sort(top, (a, b) -> {
            int r = Integer.compare(rank(a), rank(b));
            return r != 0 ? r : a.getId().compareTo(b.getId());
        });
        int hdmi = 0;
        for (TvInputInfo i : top) {
            Input in = new Input();
            in.info = i;
            if (i.getType() == TvInputInfo.TYPE_HDMI) hdmi++;
            CharSequence custom = i.loadCustomLabel(ctx);
            CharSequence label = !TextUtils.isEmpty(custom) ? custom : i.loadLabel(ctx);
            in.label = TextUtils.isEmpty(label) ? fallbackLabel(i, hdmi) : label.toString();
            in.state = state(tvm, i.getId());
            String cec = cecNames.get(i.getId());
            if (cec != null) in.detail = cec;
            else if (i.getType() == TvInputInfo.TYPE_HDMI) in.detail = stateText(in.state);
            else in.detail = "";   // AV can't tell if anything's plugged in; the tuner needs no note
            // Our own names from inputs.conf ("HDMI 2=PlayStation | Gaming"): the name on the tile, the note under it,
            // and the port's own name small in the corner.
            String mine = names.get(in.label.toLowerCase(Locale.ROOT));
            if (mine != null && !mine.trim().isEmpty()) {
                String[] parts = mine.split("\\|", 2);
                in.port = in.label;
                in.label = parts[0].trim();
                in.detail = parts.length > 1 ? parts[1].trim() : "";
            }
            out.add(in);
        }
        return out;
    }

    static Set<String> hidden(Context ctx) {
        return new HashSet<>(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, Collections.emptySet()));
    }

    static void setHidden(Context ctx, String inputId, boolean hide) {
        Set<String> s = hidden(ctx);
        if (hide) s.add(inputId); else s.remove(inputId);
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putStringSet(KEY, s).apply();
    }

    private static int state(TvInputManager tvm, String id) {
        try {
            return tvm.getInputState(id);
        } catch (RuntimeException e) {
            return TvInputManager.INPUT_STATE_CONNECTED;
        }
    }

    private static int rank(TvInputInfo i) {
        switch (i.getType()) {
            case TvInputInfo.TYPE_HDMI: return 0;
            case TvInputInfo.TYPE_TUNER: return 3;
            default: return 2;
        }
    }

    private static String fallbackLabel(TvInputInfo i, int hdmiNumber) {
        switch (i.getType()) {
            case TvInputInfo.TYPE_HDMI: return "HDMI " + hdmiNumber;
            case TvInputInfo.TYPE_TUNER: return "Antenna";
            case TvInputInfo.TYPE_COMPOSITE: return "AV";
            case TvInputInfo.TYPE_COMPONENT: return "Component";
            default: return "Input";
        }
    }

    private static String stateText(int state) {
        if (state == TvInputManager.INPUT_STATE_DISCONNECTED) return "Nothing plugged in";
        if (state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY) return "Standby";
        return "Connected";
    }
}
