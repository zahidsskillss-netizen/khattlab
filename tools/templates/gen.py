# Builds NawakhtGar templates (project JSON) and rough PNG previews to check the layout.
import json, math, os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONTS = os.path.join(os.path.dirname(__file__), '../../app/src/main/assets/fonts/')
OUT = os.path.join(os.path.dirname(__file__), '../../app/src/main/assets/templates/')
PREV = os.path.join(os.path.dirname(__file__), 'prev/')
os.makedirs(OUT, exist_ok=True); os.makedirs(PREV, exist_ok=True)

def c(h):  # '#RRGGBB' or '#AARRGGBB' -> signed int
    h = h.lstrip('#')
    if len(h) == 6: h = 'FF' + h
    v = int(h, 16)
    return v - (1 << 32) if v >= (1 << 31) else v

def rgba(v):
    v &= 0xFFFFFFFF
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, (v >> 24) & 255)

_fc = {}
def font(name, size):
    k = (name, int(size))
    if k not in _fc: _fc[k] = ImageFont.truetype(FONTS + name, int(size), layout_engine=ImageFont.Layout.RAQM)
    return _fc[k]

def measure(text, fname, size):
    f = font(fname, size)
    w = max(f.getlength(line, direction='rtl') for line in text.split('\n'))
    return w

def T(text, x, y, size, fname='Bahij_TheSansArabic-SemiBold.ttf', color='#FFFFFF', maxw=None, **kw):
    """Text layer. size shrinks so the widest line fits maxw."""
    if maxw:
        while measure(text, fname, size) > maxw and size > 10: size *= 0.95
    j = {'type': 'text', 'text': text, 'x': x, 'y': y, 'rotation': kw.pop('rotation', 0), 'scale': 1,
         'textSize': round(size, 1), 'fontKey': 'asset:' + fname, 'color': c(color), 'align': 1, 'opacity': 255}
    for k, v in kw.items():
        j[k] = c(v) if isinstance(v, str) and v.startswith('#') else v
    return j

def S(kind, x, y, w, h, fill='#FFFFFF', **kw):
    j = {'type': 'shape', 'kind': kind, 'x': x, 'y': y, 'w': w, 'h': h, 'rotation': kw.pop('rotation', 0),
         'scale': 1, 'fillMode': 0, 'fillColor': c(fill), 'opacity': kw.pop('opacity', 255)}
    for k, v in kw.items():
        j[k] = c(v) if isinstance(v, str) and v.startswith('#') else v
    return j

RECT, RR, ELL, TRI, RTRI, DIA, POLY, STAR, HEART, ARROW, LINE, LARROW, DARROW, BUBBLE, CROSS, RING, HALF, BURST = range(18)

def bg_grad(W, H, a, b, angle=90):
    return S(RECT, W / 2, H / 2, W, H, fillMode=1, grad1=a, grad2=b, gradAngle=angle, locked=True)

# ------------------------------------------------------------------ previews (rough)
def shape_poly(j):
    k, w, h = j['kind'], j['w'], j['h']
    if k == TRI: return [(w/2, 0), (w, h), (0, h)]
    if k == DIA: return [(w/2, 0), (w, h/2), (w/2, h), (0, h/2)]
    if k in (STAR, BURST):
        n = j.get('points', 5) if k == STAR else max(8, min(48, j.get('points', 5) * 3))
        ik = j.get('inner', 45) / 100 if k == STAR else 0.78
        pts = []
        for i in range(n * 2):
            a = -math.pi / 2 + i * math.pi / n; r = 1 if i % 2 == 0 else ik
            pts.append((w/2 + w/2 * r * math.cos(a), h/2 + h/2 * r * math.sin(a)))
        return pts
    if k == POLY:
        n = j.get('points', 6)
        return [(w/2 + w/2 * math.cos(-math.pi/2 + i*2*math.pi/n), h/2 + h/2*math.sin(-math.pi/2 + i*2*math.pi/n)) for i in range(n)]
    return None

def grad_img(w, h, a, b, ang):
    im = Image.new('RGBA', (w, h))
    px = im.load()
    ca, cb = rgba(a), rgba(b)
    r = math.radians(ang); dx, dy = math.cos(r), math.sin(r)
    hx, hy = dx * w / 2, dy * h / 2
    L = hx * hx + hy * hy or 1
    for y in range(h):
        for x in range(w):
            t = ((x - w/2 + hx) * hx + (y - h/2 + hy) * hy) / (2 * L)
            t = min(1, max(0, t))
            px[x, y] = tuple(int(ca[i] + (cb[i] - ca[i]) * t) for i in range(4))
    return im

def render(doc, path, scale=0.3):
    W, H = int(doc['width'] * scale), int(doc['height'] * scale)
    img = Image.new('RGBA', (W, H), rgba(doc['color']))
    for j in doc['layers']:
        if j['type'] == 'shape':
            w, h = max(2, int(j['w'] * scale)), max(2, int(j['h'] * scale))
            mask = Image.new('L', (w, h), 0); md = ImageDraw.Draw(mask)
            js = dict(j, w=w, h=h)
            k = j['kind']
            sw = int(j.get('strokeWidth', 0) * scale)
            if k == RECT: md.rectangle([0, 0, w, h], fill=255)
            elif k == RR: md.rounded_rectangle([0, 0, w-1, h-1], radius=min(w, h)/2 * j.get('corner', 20)/100, fill=255)
            elif k in (ELL,): md.ellipse([0, 0, w-1, h-1], fill=255)
            elif k == RING:
                md.ellipse([0, 0, w-1, h-1], fill=255); ik = j.get('inner', 45)/100
                md.ellipse([w*(1-ik)/2, h*(1-ik)/2, w-w*(1-ik)/2, h-h*(1-ik)/2], fill=0)
            elif k == HALF: md.pieslice([0, 0, w-1, 2*h-1], 180, 360, fill=255)
            elif k in (LINE,): md.rectangle([0, h/2 - max(1, sw)/2, w, h/2 + max(1, sw)/2], fill=255)
            elif k == HEART:
                md.ellipse([0, 0, w/2+2, h*0.6], fill=255); md.ellipse([w/2-2, 0, w, h*0.6], fill=255)
                md.polygon([(w*0.03, h*0.4), (w*0.97, h*0.4), (w/2, h)], fill=255)
            else:
                p = shape_poly(js)
                if p: md.polygon(p, fill=255)
                else: md.rectangle([0, 0, w, h], fill=255)
            if j.get('fillMode') == 2:
                fillim = Image.new('RGBA', (w, h), (0, 0, 0, 0))
            elif j.get('fillMode') == 1:
                fillim = grad_img(w, h, j['grad1'], j['grad2'], j.get('gradAngle', 90))
            else:
                fillim = Image.new('RGBA', (w, h), rgba(j['fillColor']))
            a = fillim.split()[3].point(lambda v: v * j.get('opacity', 255) // 255)
            fillim.putalpha(Image.composite(a, Image.new('L', (w, h), 0), mask))
            if sw > 0 and k not in (LINE,):
                edge = mask.filter(ImageFilter.MaxFilter(3)); inner = mask.filter(ImageFilter.MinFilter(max(3, sw | 1)))
                ring = Image.composite(edge, Image.new('L', (w, h), 0), Image.eval(inner, lambda v: 255 - v))
                fillim.paste(Image.new('RGBA', (w, h), rgba(j['strokeColor'])), (0, 0), ring)
            if j.get('rotation'):
                fillim = fillim.rotate(-j['rotation'], expand=True, resample=Image.BICUBIC)
            img.alpha_composite(fillim, (int(j['x'] * scale - fillim.width / 2), int(j['y'] * scale - fillim.height / 2)))
        else:
            f = font(j['fontKey'][6:], j['textSize'] * scale)
            lines = j['text'].split('\n')
            lh = j['textSize'] * scale * j.get('lineSpacing', 1) * 1.45
            tw = max(f.getlength(l, direction='rtl') for l in lines)
            th = lh * len(lines)
            pad = int(j['textSize'] * scale) + 10
            t = Image.new('RGBA', (int(tw) + pad * 2, int(th) + pad * 2), (0, 0, 0, 0))
            d = ImageDraw.Draw(t)
            for i, l in enumerate(lines):
                lw = f.getlength(l, direction='rtl')
                x0 = pad + (tw - lw) / 2; y0 = pad + i * lh
                if j.get('hlColor'):
                    hp = j.get('hlPad', 0) * scale
                    d.rounded_rectangle([x0 - hp, y0 - hp * 0.3, x0 + lw + hp, y0 + lh + hp * 0.1], radius=hp, fill=rgba(j['hlColor']))
                if j.get('depth'):
                    dep = j['depth'] * scale; ang = math.radians(j.get('depthAngle', 45))
                    for s in range(int(dep), 0, -1):
                        d.text((x0 + math.cos(ang) * s, y0 + math.sin(ang) * s), l, font=f, fill=rgba(j.get('depthColor', c('#555555'))), direction='rtl')
                if j.get('shadowRadius'):
                    d.text((x0 + j.get('shadowDx', 0) * scale, y0 + j.get('shadowDy', 0) * scale + 2), l, font=f, fill=(0, 0, 0, 120), direction='rtl')
                d.text((x0, y0), l, font=f, fill=rgba(j['color'] if j.get('fillMode', 0) != 1 else j['grad1']), direction='rtl',
                       stroke_width=int(j.get('strokeWidth', 0) * scale / 2), stroke_fill=rgba(j.get('strokeColor', 0)))
            if j.get('rotation'): t = t.rotate(-j['rotation'], expand=True, resample=Image.BICUBIC)
            img.alpha_composite(t, (int(j['x'] * scale - t.width / 2), int(j['y'] * scale - t.height / 2)))
    img.convert('RGB').save(path)

def save(fid, name, cat, W, H, color, layers):
    doc = {'version': 1, 'name': name, 'category': cat, 'width': W, 'height': H, 'color': c(color), 'layers': layers}
    with open(OUT + fid + '.json', 'w') as fh: json.dump(doc, fh, ensure_ascii=False)
    render(doc, PREV + fid + '.png')

exec(open(os.path.join(os.path.dirname(__file__), 'designs.py')).read())
