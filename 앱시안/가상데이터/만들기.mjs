const fs = await import("fs");
const OUT = "./"; // 이 파일이 있는 폴더에 CSV 생성
fs.mkdirSync(OUT, { recursive: true });
let seed = 20261117; const rnd = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
const pois = l => { let L = Math.exp(-l), k = 0, p = 1; do { k++; p *= rnd(); } while (p > L); return k - 1; };
const BOM = "\uFEFF"; const csv = (rows) => BOM + rows.map(r => r.map(v => { const s = String(v); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s; }).join(",")).join("\r\n") + "\r\n";

// 재료: 이름, 구매단위, 단위량(g/ml/개), 구매가격(원)
const ING = [
  ["강력분", "20kg 포대", 20000, 38000], ["박력분", "10kg 포대", 10000, 17000], ["프랑스밀가루(T65)", "20kg 포대", 20000, 52000],
  ["버터(무염, 발효버터)", "4.5kg 블록", 4500, 112000], ["설탕", "15kg 포대", 15000, 24000], ["소금", "1kg", 1000, 1500],
  ["드라이이스트", "500g", 500, 7500], ["우유", "1L", 1000, 2700], ["달걀", "30구", 30, 8400],
  ["생크림(동물성)", "1L", 1000, 9800], ["팥앙금", "5kg", 5000, 26000], ["올리브오일", "1L", 1000, 14000],
  ["아몬드가루", "1kg", 1000, 16000], ["꿀", "2.4kg", 2400, 24000], ["포장 봉투", "1000장", 1000, 45000]
];
const I = Object.fromEntries(ING.map(([n, u, q, p]) => [n, p / q]));
// 제품: 이름, 판매가, 레시피(재료: 1개당 사용량)
const PROD = [
  ["소금빵", 3500, { "강력분": 60, "버터(무염, 발효버터)": 15, "우유": 20, "설탕": 5, "소금": 2, "드라이이스트": 1, "포장 봉투": 1 }],
  ["크루아상", 4000, { "강력분": 45, "박력분": 15, "버터(무염, 발효버터)": 30, "우유": 15, "설탕": 6, "소금": 1, "드라이이스트": 1, "달걀": 0.1, "포장 봉투": 1 }],
  ["단팥빵", 2800, { "강력분": 50, "팥앙금": 50, "설탕": 6, "버터(무염, 발효버터)": 5, "우유": 15, "드라이이스트": 1, "달걀": 0.1, "포장 봉투": 1 }],
  ["우유식빵", 5500, { "강력분": 280, "우유": 150, "버터(무염, 발효버터)": 20, "설탕": 20, "소금": 5, "드라이이스트": 4, "생크림(동물성)": 20, "포장 봉투": 1 }],
  ["바게트", 4500, { "프랑스밀가루(T65)": 200, "소금": 4, "드라이이스트": 1, "포장 봉투": 1 }],
  ["치아바타", 4000, { "강력분": 120, "올리브오일": 10, "소금": 2, "드라이이스트": 1, "포장 봉투": 1 }],
  ["마들렌", 2000, { "박력분": 15, "버터(무염, 발효버터)": 15, "설탕": 13, "달걀": 0.3, "꿀": 3, "아몬드가루": 3, "포장 봉투": 1 }]
];
const cost = r => Object.entries(r).reduce((s, [k, v]) => s + I[k] * v, 0);
// 요일별 기본 수요(월=휴무): 화~일
const BASE = { "소금빵": [30, 30, 32, 38, 55, 50], "크루아상": [16, 16, 17, 20, 30, 28], "단팥빵": [14, 13, 14, 15, 18, 16], "우유식빵": [10, 10, 11, 12, 14, 12], "바게트": [8, 7, 8, 10, 14, 12], "치아바타": [9, 9, 10, 11, 13, 11], "마들렌": [20, 20, 22, 26, 40, 36] };
const DOW = ["일", "월", "화", "수", "목", "금", "토"];
const sales = [["날짜", "요일", "날씨", "제품", "생산수", "판매수(POS)", "폐기수", "품절여부", "품절시각", "판매가"]];
const daily = [];
const start = new Date("2026-08-25"), end = new Date("2026-11-15");
const hist = {}; PROD.forEach(p => hist[p[0]] = []);
for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
  const dow = d.getDay(); if (dow === 1) continue; // 월요일 휴무
  const ds = d.toISOString().slice(0, 10);
  const r = rnd(); const weather = r < 0.22 ? "비" : r < 0.55 ? "흐림" : "맑음";
  const wf = weather === "비" ? 0.8 : weather === "흐림" ? 0.95 : 1;
  const fest = ds >= "2026-10-23" && ds <= "2026-10-25"; // 주변 행사로 매장 손님 감소 가정(사장님이 축제 부스 운영)
  const event = fest ? 0.75 : 1;
  const di = dow === 0 ? 5 : dow - 2;
  for (const [name, price] of PROD) {
    const lam = BASE[name][di] * wf * event * (0.97 + 0.06 * rnd());
    const demand = pois(lam);
    const prev = hist[name].filter(h => h.dow === dow).slice(-3).map(h => h.sold);
    const plan = prev.length ? Math.round(prev.reduce((a, b) => a + b, 0) / prev.length * 1.12) : Math.round(BASE[name][di] * 1.1);
    const made = Math.max(4, plan);
    const sold = Math.min(demand, made), waste = made - sold, out = demand > made;
    let t = ""; if (out) { const frac = made / demand; const mins = 9 * 60 + Math.round(frac * 11 * 60); t = `${String(Math.floor(mins / 60)).padStart(2, "0")}:${String(mins % 60).padStart(2, "0")}`; }
    hist[name].push({ dow, sold });
    sales.push([ds, DOW[dow], weather, name, made, sold, waste, out ? "Y" : "N", t, price]);
    daily.push({ ds, name, made, sold, waste, out, price });
  }
}
fs.writeFileSync(OUT + "판매기록.csv", csv(sales));
fs.writeFileSync(OUT + "재료단가.csv", csv([["재료", "구매단위", "단위량(g·ml·개)", "구매가격(원)", "1g·ml·개당 단가(원)"], ...ING.map(([n, u, q, p]) => [n, u, q, p, (p / q).toFixed(2)])]));
const rec = [["제품", "판매가(원)", "재료", "1개당 사용량(g·ml·개)", "재료비(원)"]];
for (const [n, price, r] of PROD) for (const [k, v] of Object.entries(r)) rec.push([n, price, k, v, (I[k] * v).toFixed(1)]);
fs.writeFileSync(OUT + "레시피.csv", csv(rec));
const FIX = [["임대료", 1800000], ["파트타임 인건비(1명)", 1400000], ["전기·가스", 450000], ["수도·통신", 120000], ["POS·앱 이용료", 50000], ["기타(소모품·수리)", 150000]];
fs.writeFileSync(OUT + "유지비.csv", csv([["항목", "월 금액(원)", "비고"], ...FIX.map(([a, b]) => [a, b, "가상 값"]), ["카드 수수료", "매출의 1.1%", "가상 값 (영세 가맹점 가정)"]]));
// 요약
const days = new Set(daily.map(x => x.ds)).size;
const sum = PROD.map(([n, price, r]) => { const xs = daily.filter(x => x.name === n); const made = xs.reduce((s, x) => s + x.made, 0), sold = xs.reduce((s, x) => s + x.sold, 0), waste = xs.reduce((s, x) => s + x.waste, 0), outN = xs.filter(x => x.out).length; const c = cost(r); return { n, price, c: Math.round(c), cr: c / price, avgSold: sold / days, wasteR: waste / made, outR: outN / xs.length, contrib: Math.round((price - c) * sold - c * waste) }; });
const rev = daily.reduce((s, x) => s + x.sold * x.price, 0), mat = daily.reduce((s, x) => s + x.made * cost(PROD.find(p => p[0] === x.name)[2]), 0);
const fixMonthly = FIX.reduce((s, x) => s + x[1], 0), fixPeriod = fixMonthly * 12 / 52 * (days / 6);
console.log("영업일", days, "기간", start.toISOString().slice(0, 10), "~", "2026-11-15");
for (const s of sum) console.log(s.n, "| 판매가", s.price, "원가", s.c, "원가율", (s.cr * 100).toFixed(1) + "%", "| 하루 평균 판매", s.avgSold.toFixed(1), "| 폐기율", (s.wasteR * 100).toFixed(1) + "%", "| 품절일 비율", (s.outR * 100).toFixed(1) + "%", "| 기간 공헌이익", s.contrib);
console.log("기간 매출", Math.round(rev), "재료비(생산 기준)", Math.round(mat), "카드수수료", Math.round(rev * 0.011), "유지비(기간 환산)", Math.round(fixPeriod), "영업이익", Math.round(rev - mat - rev * 0.011 - fixPeriod), "| 하루 평균 매출", Math.round(rev / days));

