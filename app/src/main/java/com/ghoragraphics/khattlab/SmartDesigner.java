package com.ghoragraphics.khattlab;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Smart designer": turns a short request (type, texts, colors, size) into several
 * finished, fully editable designs. Works offline, without AI.
 * The result is project JSON, the same format an AI service could return later.
 */
public final class SmartDesigner {

    /** Measures text width in page pixels (the app uses Android Paint). */
    public interface Measurer {
        float width(String text, String fontFile, float size);
    }

    // ---------------------------------------------------------------- request

    public static final String[] TYPES = {
            "general", "jumma", "eid", "ramadan", "sale", "opening", "card",
            "wedding", "condolence", "course", "clinic", "news", "birthday", "post"
    };

    /** What the user asked for. */
    public static final class Request {
        public String type = "general";
        public String title = "";
        public String sub = "";
        public String name = "";
        public String phone = "";
        public String extra = "";
        public int palette = 0;
        public int size = 0;          // index in SIZES
    }

    /** width, height of each page size: square post, portrait post, story, wide (YouTube). */
    public static final int[][] SIZES = {{1080, 1080}, {1080, 1350}, {1080, 1920}, {1280, 720}};

    // ---------------------------------------------------------------- colors

    /** A color set: two background colors, accent, text on background, text on accent, light page. */
    public static final class Palette {
        final int bg1, bg2, accent, text, onAccent;
        final boolean light;

        Palette(int bg1, int bg2, int accent, int text, int onAccent, boolean light) {
            this.bg1 = bg1; this.bg2 = bg2; this.accent = accent; this.text = text; this.onAccent = onAccent; this.light = light;
        }
    }

    public static final Palette[] PALETTES = {
            new Palette(0xFF0F5132, 0xFF062A1E, 0xFFE8B547, 0xFFFFFFFF, 0xFF0B2E20, false), // 0 green + gold
            new Palette(0xFF1E1E1E, 0xFF000000, 0xFFE8B547, 0xFFFFFFFF, 0xFF111111, false), // 1 black + gold
            new Palette(0xFF13315C, 0xFF0A1931, 0xFFF3D27A, 0xFFFFFFFF, 0xFF0A1931, false), // 2 navy
            new Palette(0xFFE53935, 0xFF8E0E0E, 0xFFFFD54F, 0xFFFFFFFF, 0xFF5A0000, false), // 3 red
            new Palette(0xFF3B1466, 0xFF14062A, 0xFFF3D27A, 0xFFFFFFFF, 0xFF2A0E4A, false), // 4 purple
            new Palette(0xFFFFB74D, 0xFFE64A19, 0xFF3E2723, 0xFFFFFFFF, 0xFFFFFFFF, false), // 5 orange
            new Palette(0xFF00897B, 0xFF004D40, 0xFFFFD54F, 0xFFFFFFFF, 0xFF004D40, false), // 6 teal
            new Palette(0xFFFFF8EC, 0xFFF3E5C8, 0xFFB8862B, 0xFF3E2A12, 0xFFFFFFFF, true),  // 7 cream
            new Palette(0xFFF8BBD0, 0xFFEC407A, 0xFF4A148C, 0xFFFFFFFF, 0xFFFFFFFF, false), // 8 pink
            new Palette(0xFF2B2B2B, 0xFF0A0A0A, 0xFFBDBDBD, 0xFFEEEEEE, 0xFF111111, false), // 9 grey
            new Palette(0xFFFFFFFF, 0xFFE3F2FD, 0xFF1565C0, 0xFF0D47A1, 0xFFFFFFFF, true),  // 10 white + blue
    };

    // ---------------------------------------------------------------- fonts

    static final String RUQAA = "ArefRuqaa-Regular.ttf", AMIRI = "Amiri-Bold.ttf", BADR = "Bahij_Badr-Bold.ttf",
            HASSAN = "Bahij_Hassan-Bold.ttf", ABAN = "Bahij_Aban-Bold.ttf", GRETA = "Bahij_Greta_Arabic-Bold.ttf",
            SANS = "Bahij_TheSansArabic-SemiBold.ttf", HEL = "Bahij_Helvetica_Neue-Bold.ttf";

    // ---------------------------------------------------------------- shape kinds (see ShapeLayer)
    static final int RECT = 0, RR = 1, ELL = 2, TRI = 3, DIA = 5, STAR = 7, HEART = 8, LINE = 10, CROSS = 14,
            RING = 15, HALF = 16, BURST = 17;

    private final Measurer m;

    public SmartDesigner(Measurer m) {
        this.m = m;
    }

    // ================================================================ defaults per type

    /** Fills empty fields with the usual words for this type, and picks fitting colors. */
    public static void defaults(Request r, boolean overwrite) {
        String[] d = defaultsFor(r.type);
        if (overwrite || r.title.isEmpty()) r.title = d[0];
        if (overwrite || r.sub.isEmpty()) r.sub = d[1];
        if (overwrite || r.extra.isEmpty()) r.extra = d[2];
    }

    static String[] defaultsFor(String type) {
        switch (type) {
            case "jumma": return new String[]{"جمعه مو مبارک", "اللّٰهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ وَعَلَىٰ آلِ مُحَمَّدٍ", ""};
            case "eid": return new String[]{"اختر مو مبارک شه", "اختر مو نېکمرغه، عبادتونه مو قبول", ""};
            case "ramadan": return new String[]{"رمضان کریم", "د رحمت او برکت میاشت مو مبارک شه", ""};
            case "sale": return new String[]{"لوی تخفیف", "تر جمعې پورې", "٪۵۰"};
            case "opening": return new String[]{"پرانیستنه", "زموږ نوې څانګه ستاسو په خدمت کې ده", "کابل، شهر نو"};
            case "card": return new String[]{"ستاسو نوم", "ګرافیک ډیزاینر", "name@email.com"};
            case "wedding": return new String[]{"د واده بلنه", "احمد ❤ مریم", "جمعه، ماښام ۶ بجې"};
            case "condolence": return new String[]{"تسلیت", "د مرحوم ... د وفات په مناسبت له کورنۍ سره خواخوږي کوو", ""};
            case "course": return new String[]{"د انګلیسي ژبې کورس", "نوم لیکنه پیل شوه!", "مسلکي استادان • مناسبه فیس"};
            case "clinic": return new String[]{"ستاسو کلینیک", "روغتیا ستاسو لومړیتوب دی", "معاینه • لابراتوار • درمل"};
            case "news": return new String[]{"تازه خبر", "دلته د خبر سرلیک ولیکئ", ""};
            case "birthday": return new String[]{"د زیږون ورځ دې مبارک", "ژوند دې ډک له خوښیو وي", ""};
            case "post": return new String[]{"د ورځې خبره", "بریالیتوب د هغو کسانو دی چې هیڅکله هیله نه بایلي", ""};
            default: return new String[]{"ستاسو سرلیک", "دلته خپل متن ولیکئ", ""};
        }
    }

    /** The colors that suit a type best. */
    public static int defaultPalette(String type) {
        switch (type) {
            case "jumma": return 0;
            case "eid": return 4;
            case "ramadan": return 2;
            case "sale": return 3;
            case "opening": return 1;
            case "card": return 0;
            case "wedding": return 7;
            case "condolence": return 9;
            case "course": return 10;
            case "clinic": return 6;
            case "news": return 3;
            case "birthday": return 8;
            case "post": return 2;
            default: return 0;
        }
    }

    static boolean islamic(String t) {
        return t.equals("jumma") || t.equals("eid") || t.equals("ramadan") || t.equals("condolence");
    }

    // ================================================================ reading a typed request

    private static boolean has(String s, String... words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    /**
     * Reads a free sentence like "د جمعې پوسټ، شین رنګ، نوم احمد، 0700123456" and fills the request.
     * Returns true when a type was recognised.
     */
    public static boolean parse(String text, Request r) {
        String s = text.toLowerCase(Locale.ROOT);
        String type = null;
        if (has(s, "جمع", "jum")) type = "jumma";
        else if (has(s, "اختر", "عید", "عيد", "eid")) type = "eid";
        else if (has(s, "رمضان", "روژ", "روز", "ramadan")) type = "ramadan";
        else if (has(s, "تخفیف", "تخفيف", "sale", "off", "٪", "%")) type = "sale";
        else if (has(s, "پرانیست", "پرانست", "افتتاح", "opening")) type = "opening";
        else if (has(s, "کارت", "card")) type = "card";
        else if (has(s, "واده", "عروس", "نکاح", "wedding")) type = "wedding";
        else if (has(s, "تسلیت", "تسليت", "فاتحه", "وفات", "condolence")) type = "condolence";
        else if (has(s, "کورس", "ټولګ", "زده کړ", "course", "class")) type = "course";
        else if (has(s, "کلینیک", "كلينيك", "روغتون", "ډاکټر", "داکتر", "clinic", "doctor")) type = "clinic";
        else if (has(s, "خبر", "news")) type = "news";
        else if (has(s, "زیږون", "تولد", "birthday")) type = "birthday";
        else if (has(s, "خبره", "quote")) type = "post";

        // colors
        int pal = -1;
        if (has(s, "شین", "سبز", "green")) pal = 0;
        else if (has(s, "تور", "سیاه", "black")) pal = 1;
        else if (has(s, "آبي", "ابي", "آبی", "نیلي", "blue")) pal = 2;
        else if (has(s, "سور", "سرخ", "red")) pal = 3;
        else if (has(s, "بنفش", "purple")) pal = 4;
        else if (has(s, "نارنج", "orange")) pal = 5;
        else if (has(s, "فیروز", "teal")) pal = 6;
        else if (has(s, "طلا", "زرین", "gold", "کریم")) pal = 7;
        else if (has(s, "ګلابي", "گلابی", "pink")) pal = 8;
        else if (has(s, "خړ", "خاکستری", "grey", "gray")) pal = 9;
        else if (has(s, "سپین", "سفید", "white")) pal = 10;

        // sizes
        if (has(s, "سټوري", "ستوري", "استوری", "story", "status")) r.size = 2;
        else if (has(s, "یوټیوب", "یوتیوب", "youtube", "تمبنیل", "thumbnail")) r.size = 3;
        else if (has(s, "انسټاګرام", "instagram")) r.size = 1;

        // phone number
        Matcher ph = Pattern.compile("(\\+?93|0)\\s?7\\d[\\d\\s-]{6,10}\\d").matcher(text);
        if (ph.find()) r.phone = ph.group().trim();

        // percent for a sale
        Matcher pc = Pattern.compile("([0-9۰-۹٠-٩]{1,3})\\s*[٪%]").matcher(text);
        if (pc.find()) r.extra = "٪" + pc.group(1);

        // name: the words after "نوم" / "له لوري" / "name"
        Matcher nm = Pattern.compile("(?:نوم|نام|له لوري|از طرف|name)\\s*[:：]?\\s*([^،,\\n0-9]{2,40})").matcher(text);
        if (nm.find()) r.name = nm.group(1).trim();

        // a title in quotes
        Matcher q = Pattern.compile("[«\"“]([^»\"”]{2,60})[»\"”]").matcher(text);
        String quoted = q.find() ? q.group(1).trim() : null;

        if (type != null) {
            if (!type.equals(r.type)) {
                r.type = type;
                defaults(r, true);
            }
        } else {
            r.type = "general";
            defaults(r, true);
            // nothing recognised: the sentence itself becomes the title (if short enough)
            String t = text.trim();
            if (quoted == null && t.length() <= 40 && !t.isEmpty()) r.title = t;
        }
        if (quoted != null) r.title = quoted;
        if (r.type.equals("card") && !r.name.isEmpty()) {
            // on a business card the person's name is the big line
            r.title = r.name;
            r.name = "";
        }
        if (r.type.equals("sale") && pc.find(0)) r.extra = "٪" + pc.group(1);
        r.palette = pal >= 0 ? pal : defaultPalette(r.type);
        return type != null;
    }

    // ================================================================ building

    /** Makes all designs for the request. */
    public List<JSONObject> generate(Request r) {
        List<JSONObject> out = new ArrayList<>();
        int count = r.type.equals("card") ? 4 : 8;
        for (int v = 0; v < count; v++) {
            try {
                out.add(build(r, v));
            } catch (JSONException e) {
                // skip this variant
            }
        }
        return out;
    }

    /** One design in progress. */
    private final class B {
        final int W, H;
        final float mg, cw;
        final Palette p;
        final Request r;
        final JSONArray layers = new JSONArray();

        B(Request r, int w, int h, Palette p) {
            this.r = r; W = w; H = h; this.p = p;
            mg = Math.min(w, h) * 0.07f;
            cw = w - 2 * mg;
        }

        JSONObject shape(int kind, float x, float y, float w, float h, int fill) throws JSONException {
            JSONObject j = new JSONObject();
            j.put("type", "shape"); j.put("kind", kind);
            j.put("x", x); j.put("y", y); j.put("w", w); j.put("h", h);
            j.put("rotation", 0); j.put("scale", 1); j.put("opacity", 255);
            j.put("fillMode", 0); j.put("fillColor", fill);
            layers.put(j);
            return j;
        }

        JSONObject grad(float x, float y, float w, float h, int a, int b, float angle) throws JSONException {
            JSONObject j = shape(RECT, x, y, w, h, a);
            j.put("fillMode", 1); j.put("grad1", a); j.put("grad2", b); j.put("gradAngle", angle);
            return j;
        }

        void background() throws JSONException {
            grad(W / 2f, H / 2f, W, H, p.bg1, p.bg2, 90).put("locked", true);
        }

        /** Text that shrinks to fit [maxW]; long single lines are broken into two. */
        JSONObject text(String t, float x, float y, float size, String font, int color, float maxW) throws JSONException {
            float s = fit(t, font, size, maxW);
            if (s < size * 0.6f && !t.contains("\n") && t.trim().contains(" ")) {
                String two = split(t);
                float s2 = fit(two, font, size, maxW);
                if (s2 > s * 1.15f) { t = two; s = s2; }
            }
            JSONObject j = new JSONObject();
            j.put("type", "text"); j.put("text", t);
            j.put("x", x); j.put("y", y); j.put("rotation", 0); j.put("scale", 1); j.put("opacity", 255);
            j.put("textSize", s); j.put("fontKey", "asset:" + font); j.put("color", color);
            j.put("align", 1); j.put("lineSpacing", 1.1);
            layers.put(j);
            return j;
        }

        float fit(String t, String font, float size, float maxW) {
            float s = size;
            while (s > 12 && widest(t, font, s) > maxW) s *= 0.95f;
            return s;
        }

        float widest(String t, String font, float s) {
            float w = 0;
            for (String line : t.split("\n")) w = Math.max(w, m.width(line, font, s));
            return w;
        }

        /** Rough height of a text block (lines × size × line height). */
        float height(JSONObject t) throws JSONException {
            int lines = t.getString("text").split("\n").length;
            return (float) (t.getDouble("textSize") * 1.5 * lines);
        }

        void shadow(JSONObject t, float k) throws JSONException {
            double s = t.getDouble("textSize");
            t.put("shadowRadius", s * 0.12 * k); t.put("shadowDy", s * 0.05 * k); t.put("shadowColor", 0x77000000);
        }

        void depth(JSONObject t, int color) throws JSONException {
            double s = t.getDouble("textSize");
            t.put("depth", s * 0.08); t.put("depthAngle", 90); t.put("depthMode", 0); t.put("depthColor", color);
        }

        /** Name / phone footer at the bottom. Returns the y where the content area ends. */
        float footer(int textColor, int pillColor, int pillText) throws JSONException {
            float y = H - mg;
            if (!r.phone.isEmpty()) {
                float ph = Math.min(W, H) * 0.085f;
                y -= ph / 2;
                float pw = Math.min(cw, Math.min(W, H) * 0.62f);
                shape(RR, W / 2f, y, pw, ph, pillColor).put("corner", 100);
                text("📞 " + r.phone, W / 2f, y, ph * 0.45f, HEL, pillText, pw * 0.86f);
                y -= ph / 2 + mg * 0.4f;
            }
            if (!r.name.isEmpty()) {
                float ns = Math.min(W, H) * 0.04f;
                y -= ns * 0.7f;
                text(r.name, W / 2f, y, ns, SANS, textColor, cw);
                y -= ns * 0.9f;
            }
            return y;
        }

        /** Places blocks one under the other, centered between top and bottom. */
        void stack(List<JSONObject> blocks, float top, float bottom, float gap) throws JSONException {
            float total = 0;
            for (JSONObject b : blocks) total += height(b);
            total += gap * (blocks.size() - 1);
            float y = top + Math.max(0, (bottom - top - total) / 2f);
            for (JSONObject b : blocks) {
                float h = height(b);
                b.put("y", y + h / 2f);
                y += h + gap;
            }
        }
    }

    private String displayFont(String type, int v) {
        String[] isl = {RUQAA, AMIRI, BADR, RUQAA, AMIRI, BADR, RUQAA, ABAN};
        String[] other = {BADR, HASSAN, ABAN, GRETA, BADR, HASSAN, ABAN, BADR};
        if (type.equals("wedding")) return v % 2 == 0 ? RUQAA : BADR;
        return (islamic(type) ? isl : other)[v % 8];
    }

    private static String split(String t) {
        String[] w = t.trim().split("\\s+");
        int total = t.length(), best = 1, run = 0;
        double bestDiff = 1e9;
        for (int i = 0; i < w.length - 1; i++) {
            run += w[i].length() + 1;
            double d = Math.abs(run - total / 2.0);
            if (d < bestDiff) { bestDiff = d; best = i + 1; }
        }
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int i = 0; i < w.length; i++) (i < best ? a : b).append(i == 0 || i == best ? "" : " ").append(w[i]);
        return a + "\n" + b;
    }

    /** Small decorations that suit the type, around a point. */
    private void motif(B b, float cx, float cy, float size, int color) throws JSONException {
        String t = b.r.type;
        switch (t) {
            case "jumma": case "eid": case "ramadan":
                b.shape(STAR, cx - size * 0.9f, cy - size * 0.5f, size * 0.12f, size * 0.12f, color).put("points", 5);
                b.shape(STAR, cx + size * 0.85f, cy - size * 0.75f, size * 0.08f, size * 0.08f, color).put("points", 4);
                b.shape(STAR, cx + size * 0.6f, cy + size * 0.7f, size * 0.06f, size * 0.06f, 0xFFFFFFFF).put("points", 4);
                break;
            case "wedding": case "birthday":
                b.shape(HEART, cx - size * 0.85f, cy - size * 0.6f, size * 0.12f, size * 0.11f, color);
                b.shape(HEART, cx + size * 0.8f, cy + size * 0.65f, size * 0.09f, size * 0.08f, color);
                break;
            case "clinic":
                b.shape(CROSS, cx + size * 0.8f, cy - size * 0.7f, size * 0.12f, size * 0.12f, color);
                break;
            default:
                b.shape(ELL, cx - size * 0.85f, cy - size * 0.6f, size * 0.06f, size * 0.06f, color);
                b.shape(ELL, cx + size * 0.8f, cy + size * 0.6f, size * 0.09f, size * 0.09f, color);
        }
    }

    private static double lum(int c) {
        return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0;
    }

    private static int darker(int c, float k) {
        int r = (int) (((c >> 16) & 255) * k), g = (int) (((c >> 8) & 255) * k), b = (int) ((c & 255) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int alpha(int c, int a) {
        return (c & 0x00FFFFFF) | (a << 24);
    }

    private JSONObject build(Request r, int v) throws JSONException {
        Palette p = PALETTES[Math.max(0, Math.min(PALETTES.length - 1, r.palette))];
        int[] sz = r.type.equals("card") ? new int[]{1050, 600} : SIZES[Math.max(0, Math.min(SIZES.length - 1, r.size))];
        B b = new B(r, sz[0], sz[1], p);
        if (r.type.equals("card")) card(b, v);
        else switch (v) {
            case 0: classic(b, v); break;
            case 1: band(b, v); break;
            case 2: cardBox(b, v); break;
            case 3: badge(b, v); break;
            case 4: frame(b, v); break;
            case 5: diagonal(b, v); break;
            case 6: minimal(b, v); break;
            default: split(b, v);
        }
        JSONObject doc = new JSONObject();
        doc.put("version", 1);
        doc.put("name", r.title.replace('\n', ' '));
        doc.put("width", b.W); doc.put("height", b.H);
        doc.put("color", p.bg2);
        doc.put("layers", b.layers);
        return doc;
    }

    /** Islamic types: the Arabic line (durood, verse) is shown above the title in some layouts. */
    private boolean arabicSub(Request r) {
        return r.sub.matches(".*[\\u064B-\\u0652].*"); // has harakat
    }

    // ---------------------------------------------------------------- layouts

    /** 0: gradient, rings behind a big centered title. */
    private void classic(B b, int v) throws JSONException {
        Request r = b.r; Palette p = b.p;
        b.background();
        float s = Math.min(b.W, b.H);
        float bottom = b.footer(alpha(p.text, 0xCC), alpha(p.text, 0x22), p.text);
        float cy = (b.mg + bottom) / 2f;
        b.shape(RING, b.W / 2f, cy, s * 0.78f, s * 0.78f, alpha(p.accent, 0x44)).put("inner", 93);
        b.shape(r.type.equals("sale") ? BURST : RING, b.W / 2f, cy, s * 0.66f, s * 0.66f, alpha(p.accent, 0x22)).put("inner", 96);
        motif(b, b.W / 2f, cy, s * 0.5f, p.accent);
        List<JSONObject> blocks = new ArrayList<>();
        JSONObject title = b.text(r.title, b.W / 2f, 0, s * 0.14f, displayFont(r.type, v), p.accent, s * 0.7f);
        b.shadow(title, 1);
        blocks.add(title);
        if (!r.sub.isEmpty()) blocks.add(b.text(r.sub, b.W / 2f, 0, s * 0.045f, arabicSub(r) ? AMIRI : SANS, p.text, s * 0.68f));
        if (!r.extra.isEmpty()) blocks.add(b.text(r.extra, b.W / 2f, 0, s * 0.05f, SANS, alpha(p.text, 0xDD), s * 0.6f));
        b.stack(blocks, b.mg, bottom, s * 0.025f);
    }

    /** 1: colored band on top with the title, text in the middle, bar at the bottom. */
    private void band(B b, int v) throws JSONException {
        Request r = b.r; Palette p = b.p;
        b.shape(RECT, b.W / 2f, b.H / 2f, b.W, b.H, p.light ? p.bg1 : 0xFFFFFFFF).put("locked", true);
        float s = Math.min(b.W, b.H);
        float bandH = b.H * 0.36f;
        // on light colors the band uses the accent, so it stands out
        int ba = p.light ? p.accent : p.bg1, bb = p.light ? darker(p.accent, 0.75f) : p.bg2;
        b.grad(b.W / 2f, bandH / 2f, b.W, bandH, ba, bb, 0).put("locked", true);
        b.shape(HALF, b.W / 2f, bandH + s * 0.06f - 1, b.W * 1.1f, s * 0.12f, bb).put("rotation", 180);
        JSONObject title = b.text(r.title, b.W / 2f, bandH * 0.48f, s * 0.13f, displayFont(r.type, v), 0xFFFFFFFF, b.cw);
        b.shadow(title, 0.6f);
        int dark = p.light ? p.text : p.bg2;
        float bottom = b.footer(dark, p.light ? p.accent : p.bg1, 0xFFFFFFFF);
        List<JSONObject> blocks = new ArrayList<>();
        if (!r.sub.isEmpty()) blocks.add(b.text(r.sub, b.W / 2f, 0, s * 0.065f, arabicSub(r) ? AMIRI : BADR, dark, b.cw));
        if (!r.extra.isEmpty()) blocks.add(b.text(r.extra, b.W / 2f, 0, s * 0.05f, SANS, alpha(dark, 0xCC), b.cw));
        if (r.type.equals("clinic")) blocks.add(0, crossBlock(b, s * 0.14f, p.bg1));
        b.stack(blocks, bandH + s * 0.12f, bottom, s * 0.03f);
    }

    private JSONObject crossBlock(B b, float size, int color) throws JSONException {
        JSONObject c = b.shape(CROSS, b.W / 2f, 0, size, size, color);
        // a shape in the stack: height() reads textSize/text, so give it what it needs
        c.put("text", ""); c.put("textSize", size / 1.5f);
        return c;
    }

    /** 2: a light rounded card over the gradient. */
    private void cardBox(B b, int v) throws JSONException {
        Request r = b.r; Palette p = b.p;
        b.background();
        float s = Math.min(b.W, b.H);
        float bottom = b.footer(p.text, p.accent, p.onAccent);
        float top = b.mg;
        float ch = bottom - top, cw = b.cw;
        float cy = top + ch / 2f;
        JSONObject card = b.shape(RR, b.W / 2f, cy, cw, ch, 0xFFFFFFFF);
        card.put("corner", 12); card.put("shadowRadius", s * 0.03); card.put("shadowDy", s * 0.012); card.put("shadowColor", 0x55000000);
        b.shape(RECT, b.W / 2f, top + s * 0.012f, cw * 0.3f, s * 0.024f, p.accent);
        int dark = p.light ? p.text : p.bg2;
        List<JSONObject> blocks = new ArrayList<>();
        blocks.add(b.text(r.title, b.W / 2f, 0, s * 0.12f, displayFont(r.type, v), p.light ? p.accent : p.bg1, cw * 0.86f));
        JSONObject line = b.shape(LINE, b.W / 2f, 0, cw * 0.3f, s * 0.03f, p.accent);
        line.put("strokeWidth", s * 0.006); line.put("text", ""); line.put("textSize", s * 0.02f);
        blocks.add(line);
        if (!r.sub.isEmpty()) blocks.add(b.text(r.sub, b.W / 2f, 0, s * 0.05f, arabicSub(r) ? AMIRI : SANS, dark, cw * 0.84f));
        if (!r.extra.isEmpty()) blocks.add(b.text(r.extra, b.W / 2f, 0, s * 0.045f, SANS, alpha(dark, 0xBB), cw * 0.84f));
        b.stack(blocks, top + s * 0.04f, bottom - s * 0.04f, s * 0.03f);
    }

    /** 3: a big burst / star badge with the main word or number. */
    private void badge(B b, int v) throws JSONException {
        Request r = b.r; Palette p = b.p;
        b.background();
        float s = Math.min(b.W, b.H);
        float bottom = b.footer(p.text, 0xFFFFFFFF, p.bg2);
        JSONObject title = b.text(r.title, b.W / 2f, b.mg + s * 0.09f, s * 0.12f, displayFont(r.type, v), p.text, b.cw);
        title.put("strokeWidth", s * 0.008); title.put("strokeColor", p.bg2);
        b.shadow(title, 1);
        float top = (float) (b.mg + b.height(title) + s * 0.02f);
        float cy = (top + bottom) / 2f;
        float bs = Math.min(bottom - top, s * 0.68f);
        b.shape(BURST, b.W / 2f, cy, bs, bs, p.accent).put("points", 8);
        b.shape(BURST, b.W / 2f, cy, bs * 0.86f, bs * 0.86f, alpha(0xFFFFFFFF, 0x33)).put("points", 8);
        List<JSONObject> inner = new ArrayList<>();
        if (!r.extra.isEmpty() && r.extra.length() <= 8) {
            String big = r.extra;
            JSONObject bt = b.text(big, b.W / 2f, 0, bs * 0.3f, big.matches(".*[0-9۰-۹٠-٩].*") ? HEL : BADR, p.onAccent, bs * 0.6f);
            b.depth(bt, alpha(p.onAccent, 0x66));
            inner.add(bt);
        } else {
            // no short word or number: a star in the middle
            JSONObject st = b.shape(STAR, b.W / 2f, 0, bs * 0.26f, bs * 0.26f, p.onAccent);
            st.put("points", islamic(r.type) ? 8 : 5); st.put("inner", islamic(r.type) ? 70 : 45);
            st.put("text", ""); st.put("textSize", bs * 0.26f / 1.5f);
            inner.add(st);
        }
        if (!r.sub.isEmpty()) inner.add(b.text(r.sub, b.W / 2f, 0, bs * 0.07f, SANS, p.onAccent, bs * 0.58f));
        b.stack(inner, cy - bs * 0.35f, cy + bs * 0.35f, bs * 0.02f);
    }

    /** 4: light paper with a double frame, elegant. */
    private void frame(B b, int v) throws JSONException {
        Request r = b.r; Palette p = b.p;
        int paper = p.light ? p.bg1 : 0xFFFFF8EC;
        int ink = p.light ? p.text : p.bg2;
        // the accent must be dark enough to read on light paper
        int gold = p.light ? p.accent : (lum(p.accent) > 0.62 ? p.bg1 : p.accent);
        b.shape(RECT, b.W / 2f, b.H / 2f, b.W, b.H, paper).put("locked", true);
        float s = Math.min(b.W, b.H);
        JSONObject f1 = b.shape(RR, b.W / 2f, b.H / 2f, b.W - b.mg, b.H - b.mg, 0);
        f1.put("fillMode", 2); f1.put("strokeWidth", s * 0.007); f1.put("strokeColor", gold); f1.put("corner", 4);
        JSONObject f2 = b.shape(RR, b.W / 2f, b.H / 2f, b.W - b.mg * 1.5f, b.H - b.mg * 1.5f, 0);
        f2.put("fillMode", 2); f2.put("strokeWidth", s * 0.0025); f2.put("strokeColor", gold); f2.put("corner", 4);
        b.shape(DIA, b.W / 2f, b.mg * 0.5f, s * 0.035f, s * 0.035f, gold);
        b.shape(DIA, b.W / 2f, b.H - b.mg * 0.5f, s * 0.035f, s * 0.035f, gold);
        float bottom = b.footer(alpha(ink, 0xCC), gold, 0xFFFFFFFF) - b.mg * 0.3f;
        List<JSONObject> blocks = new ArrayList<>();
        if (r.type.equals("wedding")) {
            JSONObject rg = b.shape(RING, b.W / 2f - s * 0.06f, 0, s * 0.16f, s * 0.16f, gold);
            rg.put("inner", 82); rg.put("text", ""); rg.put("textSize", s * 0.11f);
            blocks.add(rg);
        }
        blocks.add(b.text(r.title, b.W / 2f, 0, s * 0.12f, islamic(r.type) || r.type.equals("wedding") ? RUQAA : BADR, gold, b.cw * 0.86f));
        if (!r.sub.isEmpty()) blocks.add(b.text(r.sub, b.W / 2f, 0, s * 0.06f, arabicSub(r) ? AMIRI : BADR, ink, b.cw * 0.84f));
        JSONObject line = b.shape(LINE, b.W / 2f, 0, s * 0.3f, s * 0.02f, gold);
        line.put("strokeWidth", s * 0.004); line.put("text", ""); line.put("textSize", s * 0.015f);
        blocks.add(line);
        if (!r.extra.isEmpty()) blocks.add(b.text(r.extra, b.W / 2f, 0, s * 0.045f, SANS, ink, b.cw * 0.84f));
        b.stack(blocks, b.mg * 1.4f, bottom, s * 0.03f);
        // the second ring of a wedding sits beside the first
        if (r.type.equals("wedding")) {
            JSONObject first = blocks.get(0);
            JSONObject rg2 = b.shape(RING, b.W / 2f + s * 0.06f, (float) first.getDouble("y"), s * 0.16f, s * 0.16f, gold);
            rg2.put("inner", 82);
        }
    }

    /** 5: a wide tilted stripe across the page with the title on it. */
    private void diagonal(B b, int v) throws JSONException {
        Request r = b.r; Palette p = b.p;
        b.background();
        float s = Math.min(b.W, b.H);
        b.shape(ELL, b.W * 0.92f, b.H * 0.08f, s * 0.5f, s * 0.5f, alpha(0xFFFFFFFF, 0x18));
        b.shape(ELL, b.W * 0.06f, b.H * 0.93f, s * 0.6f, s * 0.6f, alpha(0xFFFFFFFF, 0x14));
        float bottom = b.footer(p.text, alpha(0xFFFFFFFF, 0x26), p.text);
        float cy = (b.mg + bottom) / 2f - s * 0.05f;
        JSONObject stripe = b.shape(RECT, b.W / 2f, cy, b.W * 1.5f, s * 0.24f, p.accent);
        stripe.put("rotation", -7);
        stripe.put("shadowRadius", s * 0.02); stripe.put("shadowDy", s * 0.01); stripe.put("shadowColor", 0x55000000);
        JSONObject title = b.text(r.title, b.W / 2f, cy, s * 0.13f, displayFont(r.type, v), p.onAccent, b.cw * 0.95f);
        title.put("rotation", -7);
        float y = cy + s * 0.2f;
        if (!r.sub.isEmpty()) {
            JSONObject st = b.text(r.sub, b.W / 2f, 0, s * 0.05f, arabicSub(r) ? AMIRI : SANS, p.text, b.cw);
            y += b.height(st) / 2f;
            st.put("y", y);
            y += b.height(st) / 2f + s * 0.02f;
        }
        if (!r.extra.isEmpty()) {
            JSONObject et = b.text(r.extra, b.W / 2f, 0, s * 0.045f, SANS, alpha(p.text, 0xCC), b.cw);
            et.put("y", y + b.height(et) / 2f);
        }
        if (!arabicSub(r) && islamic(r.type)) motif(b, b.W / 2f, cy - s * 0.25f, s * 0.4f, p.accent);
    }

    /** 6: one plain color, big title, thin accent lines and corner circles. */
    private void minimal(B b, int v) throws JSONException {
        Request r = b.r; Palette p = b.p;
        b.shape(RECT, b.W / 2f, b.H / 2f, b.W, b.H, p.bg1).put("locked", true);
        float s = Math.min(b.W, b.H);
        JSONObject c1 = b.shape(RING, 0, 0, s * 0.5f, s * 0.5f, alpha(p.accent, 0x66));
        c1.put("inner", 80);
        JSONObject c2 = b.shape(ELL, b.W, b.H, s * 0.45f, s * 0.45f, alpha(p.accent, 0x55));
        float bottom = b.footer(p.text, p.accent, p.onAccent);
        List<JSONObject> blocks = new ArrayList<>();
        JSONObject l1 = b.shape(LINE, b.W / 2f, 0, s * 0.18f, s * 0.02f, p.accent);
        l1.put("strokeWidth", s * 0.008); l1.put("text", ""); l1.put("textSize", s * 0.015f);
        blocks.add(l1);
        blocks.add(b.text(r.title, b.W / 2f, 0, s * 0.15f, displayFont(r.type, v + 1), p.text, b.cw));
        if (!r.sub.isEmpty()) blocks.add(b.text(r.sub, b.W / 2f, 0, s * 0.05f, arabicSub(r) ? AMIRI : SANS, p.accent, b.cw));
        if (!r.extra.isEmpty()) blocks.add(b.text(r.extra, b.W / 2f, 0, s * 0.042f, SANS, alpha(p.text, 0xBB), b.cw));
        b.stack(blocks, b.mg, bottom, s * 0.03f);
    }

    /** 7: colored top part with shapes, light bottom part with the text, title in a pill between. */
    private void split(B b, int v) throws JSONException {
        Request r = b.r; Palette p = b.p;
        b.shape(RECT, b.W / 2f, b.H / 2f, b.W, b.H, 0xFFFFFFFF).put("locked", true);
        float s = Math.min(b.W, b.H);
        float topH = b.H * 0.48f;
        if (p.light) b.grad(b.W / 2f, topH / 2f, b.W, topH, darker(p.accent, 0.9f), darker(p.accent, 0.65f), 45).put("locked", true);
        else b.grad(b.W / 2f, topH / 2f, b.W, topH, p.bg1, p.bg2, 45).put("locked", true);
        b.shape(RING, b.W * 0.25f, topH * 0.45f, s * 0.42f, s * 0.42f, alpha(p.accent, 0x55)).put("inner", 75);
        b.shape(ELL, b.W * 0.78f, topH * 0.35f, s * 0.26f, s * 0.26f, alpha(0xFFFFFFFF, 0x22));
        motif(b, b.W / 2f, topH * 0.45f, s * 0.45f, p.accent);
        float pw = b.cw * 0.92f, ph = s * 0.17f;
        JSONObject pill = b.shape(RR, b.W / 2f, topH, pw, ph, p.light ? 0xFFFFFFFF : p.accent);
        pill.put("corner", 100); pill.put("shadowRadius", s * 0.02); pill.put("shadowDy", s * 0.01); pill.put("shadowColor", 0x44000000);
        b.text(r.title, b.W / 2f, topH, ph * 0.55f, displayFont(r.type, v), p.light ? p.accent : p.onAccent, pw * 0.88f);
        int dark = p.light ? p.text : p.bg2;
        float bottom = b.footer(dark, p.bg1, 0xFFFFFFFF);
        List<JSONObject> blocks = new ArrayList<>();
        if (!r.sub.isEmpty()) blocks.add(b.text(r.sub, b.W / 2f, 0, s * 0.065f, arabicSub(r) ? AMIRI : BADR, dark, b.cw));
        if (!r.extra.isEmpty()) blocks.add(b.text(r.extra, b.W / 2f, 0, s * 0.05f, SANS, alpha(dark, 0xBB), b.cw));
        b.stack(blocks, topH + ph / 2f + s * 0.03f, bottom, s * 0.03f);
    }

    /** Business card (landscape) in four looks. */
    private void card(B b, int v) throws JSONException {
        Request r = b.r; Palette p = b.p;
        int W = b.W, H = b.H;
        boolean darkPage = v % 2 == 1;
        b.shape(RECT, W / 2f, H / 2f, W, H, darkPage ? p.bg2 : 0xFFFFFFFF).put("locked", true);
        int ink = darkPage ? 0xFFFFFFFF : (p.light ? p.text : p.bg1);
        float side = W * 0.36f;
        String initial = r.title.trim().isEmpty() ? "G" : r.title.trim().substring(0, 1);
        float sideX = v < 2 ? W - side / 2f : side / 2f;
        float textX = v < 2 ? (W - side) / 2f : side + (W - side) / 2f;
        if (v % 2 == 0) b.grad(sideX, H / 2f, side, H, p.bg1, p.bg2, 90).put("locked", true);
        else b.shape(RECT, sideX, H / 2f, side, H, alpha(p.accent, 0x22)).put("locked", true);
        b.shape(ELL, sideX, H * 0.38f, H * 0.3f, H * 0.3f, p.accent);
        b.text(initial, sideX, H * 0.38f, H * 0.17f, BADR, p.onAccent, H * 0.25f);
        if (!r.name.isEmpty()) b.text(r.name, sideX, H * 0.72f, H * 0.065f, BADR, darkPage ? p.accent : 0xFFFFFFFF, side * 0.85f);
        float tw = W - side - b.mg * 2;
        b.text(r.title, textX, H * 0.26f, H * 0.12f, BADR, darkPage ? 0xFFFFFFFF : ink, tw);
        if (!r.sub.isEmpty()) b.text(r.sub, textX, H * 0.42f, H * 0.065f, SANS, p.accent == 0xFFFFFFFF ? p.bg1 : p.accent, tw);
        JSONObject ln = b.shape(LINE, textX, H * 0.52f, tw * 0.7f, H * 0.03f, darkPage ? 0x55FFFFFF : 0x22000000);
        ln.put("strokeWidth", 3);
        if (!r.phone.isEmpty()) b.text("📞 " + r.phone, textX, H * 0.65f, H * 0.055f, HEL, ink, tw);
        if (!r.extra.isEmpty()) b.text(r.extra, textX, H * 0.78f, H * 0.05f, HEL, alpha(ink, 0xCC), tw);
    }
}
