// 기록 기간(주)에 따라 예측 오차가 어떻게 바뀌는지 가상 데이터로 시험 (모두 가상 값)
// 수요 모델은 만들기.mjs와 같음: 요일별 기본 수요 × 날씨(비 0.8, 흐림 0.95) × 작은 흔들림 → 포아송
// 실행: ego-browser nodejs -e 'await import("file:///.../예측시험.mjs")'
const fs = await import("fs");
const OUT = "C:/Users/Administrator/Desktop/llm_local/앱시안/가상데이터/예측시험결과.json";
const BASE = { "소금빵": [30, 30, 32, 38, 55, 50], "크루아상": [16, 16, 17, 20, 30, 28], "단팥빵": [14, 13, 14, 15, 18, 16], "우유식빵": [10, 10, 11, 12, 14, 12], "바게트": [8, 7, 8, 10, 14, 12], "치아바타": [9, 9, 10, 11, 13, 11], "마들렌": [20, 20, 22, 26, 40, 36] };
const PRODS = Object.keys(BASE);
const WF = { "비": 0.8, "흐림": 0.95, "맑음": 1 };
let seed = 1;
const rnd = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
const pois = l => { let L = Math.exp(-l), k = 0, p = 1; do { k++; p *= rnd(); } while (p > L); return k - 1; };

// 영업일 시퀀스 생성 (주 6일, 화~일 → di 0~5)
function simulate(weeks) {
  const days = [];
  for (let w = 0; w < weeks; w++) for (let di = 0; di < 6; di++) {
    const r = rnd(); const weather = r < 0.22 ? "비" : r < 0.55 ? "흐림" : "맑음";
    const row = { di, weather, lam: {}, d: {} };
    for (const p of PRODS) { const lam = BASE[p][di] * WF[weather] * (0.97 + 0.06 * rnd()); row.lam[p] = lam; row.d[p] = pois(lam); }
    days.push(row);
  }
  return days;
}

// 방법
// A 사장님 방식: 같은 요일 최근 3주 평균 (기록이 3주 미만이면 있는 만큼)
// B 같은 요일 전체 평균 (가진 기록 전부)
// C B + 날씨 보정: 기록에서 날씨별 (실제 ÷ 같은 요일 평균) 비율을 모든 제품 합쳐 추정. 해당 날씨 기록이 3일 미만이면 보정 안 함
function prep(hist) {
  const mean = {}, last3 = {};
  for (let di = 0; di < 6; di++) {
    const same = hist.filter(h => h.di === di); mean[di] = {}; last3[di] = {};
    const s3 = same.slice(-3);
    for (const p of PRODS) { mean[di][p] = same.reduce((a, h) => a + h.d[p], 0) / same.length; last3[di][p] = s3.reduce((a, h) => a + h.d[p], 0) / s3.length; }
  }
  // 날씨별 비율: (그 날씨 날 실제 합 ÷ 같은 요일 평균 합), 모든 제품 합침
  const ratio = {};
  for (const w of Object.keys(WF)) {
    const wd = hist.filter(h => h.weather === w);
    if (wd.length < 3) { ratio[w] = 1; continue; }
    let num = 0, den = 0;
    for (const h of wd) for (const p of PRODS) { num += h.d[p]; den += mean[h.di][p]; }
    ratio[w] = num / den;
  }
  return { mean, last3, ratio };
}
function forecast(P, day, p, method) {
  if (method === "A") return P.last3[day.di][p];
  if (method === "B") return P.mean[day.di][p];
  return P.mean[day.di][p] * P.ratio[day.weather];
}

const HIST = [2, 4, 8, 12, 26, 52];
const TEST_WEEKS = 4, REPS = 300;
const methods = ["A", "B", "C", "참값"];
const acc = {}; // acc[N][method][prod] = {abs, act}
for (const N of HIST) { acc[N] = {}; for (const m of methods) { acc[N][m] = {}; for (const p of [...PRODS, "가게전체"]) acc[N][m][p] = { abs: 0, act: 0 }; } }

for (let r = 0; r < REPS; r++) {
  seed = 1000 + r;
  const all = simulate(52 + TEST_WEEKS);
  const test = all.slice(52 * 6);
  for (const N of HIST) {
    const hist = all.slice((52 - N) * 6, 52 * 6); // 시험 기간 바로 앞 N주
    const P = prep(hist);
    for (const day of test) for (const m of methods) {
      let tf = 0, ta = 0;
      for (const p of PRODS) {
        const f = m === "참값" ? day.lam[p] : forecast(P, day, p, m);
        const a = day.d[p];
        acc[N][m][p].abs += Math.abs(f - a); acc[N][m][p].act += a;
        tf += f; ta += a;
      }
      acc[N][m]["가게전체"].abs += Math.abs(tf - ta); acc[N][m]["가게전체"].act += ta;
    }
  }
}
const res = {};
for (const N of HIST) { res[N] = {}; for (const m of methods) { res[N][m] = {}; for (const p of [...PRODS, "가게전체"]) res[N][m][p] = +(100 * acc[N][m][p].abs / acc[N][m][p].act).toFixed(1); } }
fs.writeFileSync(OUT, JSON.stringify({ 설명: "값 = 오차율(%) = 절대오차 합 ÷ 실제 판매 합, 시험 기간 4주, 반복 300회", res }, null, 1));
for (const N of HIST) console.log(N + "주", JSON.stringify(Object.fromEntries(methods.map(m => [m, res[N][m]["가게전체"] + "/" + res[N][m]["바게트"] + "/" + res[N][m]["소금빵"]]))));
