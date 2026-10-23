BADR = 'Bahij_Badr-Bold.ttf'; ABAN = 'Bahij_Aban-Bold.ttf'; HASSAN = 'Bahij_Hassan-Bold.ttf'
RUQAA = 'ArefRuqaa-Regular.ttf'; AMIRI = 'Amiri-Bold.ttf'; SCH = 'ScheherazadeNew-Bold.ttf'
SANS = 'Bahij_TheSansArabic-SemiBold.ttf'; HEL = 'Bahij_Helvetica_Neue-Bold.ttf'; GRETA = 'Bahij_Greta_Arabic-Bold.ttf'

# 1 Jumu'ah
W = H = 1080
save('01_jumma', 'جمعه مبارک', 'islamic', W, H, '#0B3D2E', [
    bg_grad(W, H, '#0F5132', '#062A1E', 90),
    S(RING, W/2, 470, 760, 760, fill='#33E8B547', inner=92),
    S(RING, W/2, 470, 660, 660, fill='#22E8B547', inner=96),
    S(BURST, W/2, 470, 560, 560, fill='#14FFFFFF', points=8),
    T('جمعه مو مبارک', W/2, 430, 150, RUQAA, '#F3D27A', maxw=820, depth=10, depthAngle=90, depthMode=0, depthColor='#8A6A1C',
      shadowRadius=14, shadowDy=6, shadowColor='#66000000'),
    T('اللّٰهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ وَعَلَىٰ آلِ مُحَمَّدٍ', W/2, 630, 46, AMIRI, '#FFFFFF', maxw=760),
    S(LINE, W/2, 760, 260, 40, fill='#E8B547', strokeWidth=4),
    S(DIA, W/2, 760, 26, 26, fill='#E8B547'),
    T('ستاسو نوم یا پاڼه', W/2, 950, 40, SANS, '#CCFFFFFF', maxw=700),
])

# 2 Eid
W, H = 1080, 1350
save('02_eid', 'اختر مبارک', 'islamic', W, H, '#2A0E4A', [
    bg_grad(W, H, '#3B1466', '#14062A', 90),
    S(STAR, 180, 200, 70, 70, fill='#E8B547', points=5, inner=45),
    S(STAR, 900, 280, 46, 46, fill='#F3D27A', points=5, inner=45),
    S(STAR, 820, 140, 30, 30, fill='#FFFFFF', points=4, inner=35),
    S(STAR, 260, 420, 28, 28, fill='#FFFFFF', points=4, inner=35),
    S(ELL, W/2, 560, 520, 520, fill='#22E8B547'),
    S(ELL, W/2 + 70, 520, 400, 400, fill='#3B1466'),
    T('اختر مو مبارک شه', W/2, 800, 140, BADR, '#F3D27A', maxw=900, fillMode=1, grad1='#FFE7A3', grad2='#D49A2A', gradAngle=90,
      shadowRadius=18, shadowDy=8, shadowColor='#88000000'),
    T('اختر مو نېکمرغه، عبادتونه مو قبول', W/2, 950, 52, SANS, '#FFFFFF', maxw=860),
    T('له لوري: ستاسو نوم', W/2, 1190, 40, SANS, '#B3FFFFFF', maxw=700),
])

# 3 Ramadan
W = H = 1080
save('03_ramadan', 'رمضان کریم', 'islamic', W, H, '#0A1931', [
    bg_grad(W, H, '#13315C', '#0A1931', 90),
    S(ELL, 790, 260, 260, 260, fill='#F3D27A'),
    S(ELL, 850, 220, 230, 230, fill='#13315C'),
    S(STAR, 200, 180, 40, 40, fill='#FFFFFF', points=4, inner=35),
    S(STAR, 330, 300, 26, 26, fill='#FFFFFF', points=4, inner=35),
    S(STAR, 620, 140, 30, 30, fill='#F3D27A', points=5, inner=45),
    T('رمضان کریم', W/2, 560, 190, RUQAA, '#F3D27A', maxw=860, shadowRadius=20, shadowDy=6, shadowColor='#88000000'),
    T('د رحمت او برکت میاشت مو مبارک شه', W/2, 740, 50, SANS, '#FFFFFF', maxw=860),
    S(RR, W/2, 920, 520, 90, fill='#22FFFFFF', corner=100),
    T('ستاسو نوم', W/2, 920, 40, SANS, '#FFFFFF', maxw=460),
])

# 4 Sale
W, H = 1080, 1350
save('04_sale', 'لوی تخفیف', 'business', W, H, '#B71C1C', [
    bg_grad(W, H, '#E53935', '#8E0E0E', 90),
    S(BURST, W/2, 560, 760, 760, fill='#FFD54F', points=8),
    S(BURST, W/2, 560, 650, 650, fill='#FFC107', points=8),
    T('لوی تخفیف', W/2, 200, 130, BADR, '#FFFFFF', maxw=900, strokeWidth=10, strokeColor='#5A0000',
      shadowRadius=10, shadowDy=8, shadowColor='#66000000'),
    T('٪۵۰', W/2, 520, 260, HEL, '#B71C1C', maxw=520, depth=14, depthAngle=90, depthMode=0, depthColor='#7A0E0E'),
    T('تر جمعې پورې', W/2, 700, 60, SANS, '#5A0000', maxw=480),
    S(RR, W/2, 1050, 760, 120, fill='#FFFFFF', corner=100),
    T('همدا اوس راشئ!', W/2, 1050, 60, BADR, '#B71C1C', maxw=700),
    T('📞 0700 000 000', W/2, 1220, 46, HEL, '#FFFFFF', maxw=700),
])

# 5 Grand opening
W = H = 1080
save('05_opening', 'نوې پرانیستنه', 'business', W, H, '#111111', [
    bg_grad(W, H, '#1E1E1E', '#000000', 90),
    S(RECT, W/2, 120, W, 16, fill='#E8B547'),
    S(RECT, W/2, 960, W, 16, fill='#E8B547'),
    T('په زړه پورې خبر', W/2, 260, 56, SANS, '#E8B547', maxw=800),
    T('پرانیستنه', W/2, 440, 210, BADR, '#FFFFFF', maxw=920, depth=12, depthAngle=90, depthMode=0, depthColor='#E8B547'),
    T('زموږ نوې څانګه ستاسو په خدمت کې ده', W/2, 640, 50, SANS, '#FFFFFF', maxw=880),
    S(RR, W/2, 800, 640, 100, fill='#E8B547', corner=100),
    T('کابل، شهر نو، لومړی سړک', W/2, 800, 44, SANS, '#111111', maxw=580),
])

# 6 Business card
W, H = 1050, 600
save('06_card', 'د کار کارت', 'business', W, H, '#FFFFFF', [
    S(RECT, 860, H/2, 380, H, fill='#0F5132'),
    S(ELL, 860, 230, 170, 170, fill='#E8B547'),
    T('G', 860, 228, 100, HEL, '#0F5132'),
    T('ستاسو شرکت', 860, 420, 44, BADR, '#FFFFFF', maxw=330),
    T('ستاسو نوم', 330, 170, 74, BADR, '#0F5132', maxw=560),
    T('ګرافیک ډیزاینر', 330, 260, 40, SANS, '#E8B547', maxw=540),
    S(LINE, 330, 320, 420, 20, fill='#DDDDDD', strokeWidth=3),
    T('📞 0700 000 000', 330, 400, 34, HEL, '#333333', maxw=520),
    T('✉ name@email.com', 330, 470, 30, HEL, '#333333', maxw=520),
])

# 7 YouTube thumbnail
W, H = 1280, 720
save('07_youtube', 'یوټیوب تمبنیل', 'social', W, H, '#101010', [
    bg_grad(W, H, '#202020', '#000000', 0),
    S(RECT, 1060, H/2, 440, H, fill='#E53935'),
    S(TRI, 740, H/2, H, 200, fill='#E53935', rotation=-90),
    T('ستاسو\nسرلیک', 470, 300, 150, BADR, '#FFEB3B', maxw=760, strokeWidth=14, strokeColor='#000000', lineSpacing=1.0,
      shadowRadius=12, shadowDx=6, shadowDy=6, shadowColor='#AA000000'),
    S(RR, 380, 600, 400, 90, fill='#FFFFFF', corner=40),
    T('نوې ویډیو', 380, 600, 52, BADR, '#E53935', maxw=360),
    S(ARROW, 1060, 360, 260, 150, fill='#FFFFFF', rotation=-25),
])

# 8 Story quote
W, H = 1080, 1920
save('08_story_quote', 'د ورځې خبره', 'social', W, H, '#3949AB', [
    bg_grad(W, H, '#5C6BC0', '#1A237E', 120),
    S(ELL, 900, 260, 420, 420, fill='#18FFFFFF'),
    S(ELL, 150, 1650, 520, 520, fill='#14FFFFFF'),
    T('د ورځې خبره', W/2, 420, 80, BADR, '#FFFFFF', maxw=800),
    T('❝', W/2, 640, 220, AMIRI, '#66FFFFFF'),
    T('بریالیتوب د هغو کسانو دی\nچې هیڅکله هیله نه بایلي', W/2, 960, 76, SANS, '#FFFFFF', maxw=900, lineSpacing=1.3),
    S(LINE, W/2, 1220, 200, 30, fill='#E8B547', strokeWidth=6),
    T('ستاسو نوم', W/2, 1320, 52, SANS, '#E8B547', maxw=700),
])

# 9 Condolence
W = H = 1080
save('09_condolence', 'تسلیت', 'events', W, H, '#1C1C1C', [
    bg_grad(W, H, '#2B2B2B', '#0A0A0A', 90),
    S(RING, W/2, H/2, 900, 900, fill='#33FFFFFF', inner=97),
    T('إِنَّا لِلّٰهِ وَإِنَّا إِلَيْهِ رَاجِعُونَ', W/2, 300, 64, AMIRI, '#E0E0E0', maxw=820),
    T('تسلیت', W/2, 500, 170, BADR, '#FFFFFF', maxw=700),
    T('د مرحوم ... د وفات په مناسبت\nله کورنۍ سره د زړه له کومي خواخوږي کوو', W/2, 700, 44, SANS, '#CCCCCC', maxw=820, lineSpacing=1.3),
    T('ستاسو نوم', W/2, 880, 40, SANS, '#9E9E9E', maxw=600),
])

# 10 Wedding
W, H = 1080, 1350
save('10_wedding', 'د واده بلنه', 'events', W, H, '#FFF8EC', [
    S(RR, W/2, H/2, 960, 1230, fill='#00000000', fillMode=2, strokeWidth=6, strokeColor='#C9A227', corner=6),
    S(RR, W/2, H/2, 920, 1190, fill='#00000000', fillMode=2, strokeWidth=2, strokeColor='#C9A227', corner=6),
    S(RING, 470, 300, 170, 170, fill='#C9A227', inner=82),
    S(RING, 600, 300, 170, 170, fill='#C9A227', inner=82),
    T('د واده بلنه', W/2, 500, 120, RUQAA, '#8A6A1C', maxw=820),
    T('احمد  ❤  مریم', W/2, 680, 84, BADR, '#3E2A12', maxw=820),
    T('تاسو او ستاسو کورنۍ ته بلنه درکوو', W/2, 830, 44, SANS, '#5D4037', maxw=820),
    S(LINE, W/2, 920, 300, 20, fill='#C9A227', strokeWidth=3),
    T('جمعه، ۱۴۰۵/۸/۱۵ — ماښام ۶ بجې\nد ګل محل تالار', W/2, 1040, 44, SANS, '#3E2A12', maxw=820, lineSpacing=1.3),
])

# 11 Clinic
W = H = 1080
save('11_clinic', 'کلینیک اعلان', 'business', W, H, '#E0F2F1', [
    S(RECT, W/2, 160, W, 320, fill='#00897B'),
    S(HALF, W/2, 330, W, 140, fill='#00897B', rotation=180),
    S(CROSS, W/2, 560, 220, 220, fill='#00897B'),
    T('ستاسو کلینیک', W/2, 150, 110, BADR, '#FFFFFF', maxw=900),
    T('روغتیا ستاسو لومړیتوب دی', W/2, 760, 64, BADR, '#004D40', maxw=900),
    T('معاینه • لابراتوار • درمل', W/2, 860, 46, SANS, '#00695C', maxw=860),
    S(RR, W/2, 980, 620, 90, fill='#00897B', corner=100),
    T('📞 0700 000 000', W/2, 980, 42, HEL, '#FFFFFF', maxw=560),
])

# 12 Course
W = H = 1080
save('12_course', 'کورس اعلان', 'business', W, H, '#FFFFFF', [
    S(ELL, 1010, 70, 380, 380, fill='#FFC107'),
    S(ELL, 120, 980, 420, 420, fill='#1E88E5'),
    T('د انګلیسي ژبې کورس', W/2, 330, 100, BADR, '#0D47A1', maxw=900),
    S(RR, W/2, 500, 560, 100, fill='#E53935', corner=100),
    T('نوم لیکنه پیل شوه!', W/2, 500, 54, BADR, '#FFFFFF', maxw=520),
    T('✔ مسلکي استادان\n✔ مناسبه فیس\n✔ سهار او ماښام ټولګي', W/2, 720, 50, SANS, '#263238', maxw=800, lineSpacing=1.35),
    T('📞 0700 000 000', W/2, 950, 44, HEL, '#0D47A1', maxw=600),
])

# 13 Breaking news
W, H = 1280, 720
save('13_news', 'تازه خبر', 'social', W, H, '#0D1B2A', [
    bg_grad(W, H, '#1B263B', '#0D1B2A', 90),
    S(RECT, W/2, 560, W, 150, fill='#C62828'),
    S(RECT, 1110, 470, 340, 70, fill='#FFFFFF'),
    T('تازه خبر', 1110, 470, 46, BADR, '#C62828', maxw=300),
    T('دلته د خبر سرلیک ولیکئ', W/2, 560, 70, BADR, '#FFFFFF', maxw=1180),
    T('ستاسو رسنۍ', 140, 80, 40, BADR, '#FFFFFF', maxw=260),
    S(ELL, 1200, 80, 30, 30, fill='#E53935'),
    T('ژوندۍ', 1120, 80, 34, SANS, '#FFFFFF'),
])

# 14 Motivation
W = H = 1080
save('14_motivation', 'هیله مه بایله', 'social', W, H, '#FF7043', [
    bg_grad(W, H, '#FFB74D', '#E64A19', 135),
    S(RING, 860, 220, 300, 300, fill='#33FFFFFF', inner=70),
    S(RING, 200, 880, 240, 240, fill='#33FFFFFF', inner=70),
    T('هیڅکله', W/2, 390, 150, BADR, '#FFFFFF', maxw=860, shadowRadius=16, shadowDy=6, shadowColor='#55000000'),
    T('هیله مه بایله', W/2, 580, 130, BADR, '#3E2723', maxw=900),
    T('هره ورځ یو نوی پیل دی', W/2, 760, 54, SANS, '#FFFFFF', maxw=860),
])
