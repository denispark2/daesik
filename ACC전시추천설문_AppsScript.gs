/**
 * ACC 전시 추천 설문 웹앱 — Code.gs (서버)
 * 화면: ACC전시추천설문_Index.html → Apps Script 프로젝트에 'Index' 이름의 HTML 파일로 넣음
 * 알고리즘: ACC현재전시_그룹별추천_시뮬레이션.md 1번 (3판 최종, 2026-11-25 보완 포함)
 *
 * 쓰는 법 (자세한 건 ACC전시추천설문.md)
 *  1. script.google.com → 새 프로젝트 → Code.gs에 이 파일 전체 붙여넣기
 *  2. 파일 + → HTML → 이름 Index → ACC전시추천설문_Index.html 전체 붙여넣기 → 저장
 *  3. 함수 선택에서 setupSheet 실행 → 권한 허용 → 로그에 응답 시트 주소
 *  4. 배포 → 새 배포 → 웹 앱 (실행: 나, 액세스: 모든 사용자) → 웹 앱 주소가 설문 주소
 *  - 흐름: 문항 체크 → 결과보기(추천 전시 카드, 클릭 시 ACC 공식 안내) → 추가질문 → 제출 → 시트 저장
 */

// ───────────── 1. 전시 데이터 (2026-11-24 기준, 1-5번 표) ─────────────
var EXHIBITIONS = {
  '인상파, 찬란한 순간들': { short: '인상파', building: '문화창조원', minutes: 60, paid: true,
    start: '2026-09-23', end: '2027-02-21', reviews: 232, tags: { '명화·그림': 2, '체험': 1, '미디어·사운드': 1 },
    fee: '유료: 성인 13,000원 · 청소년·어린이 10,000원 (할인은 예매처 확인)',
    hours: 'both', entry: 'minus60' },
  '길 위의 노마드': { short: '노마드', building: '아시아문화박물관', minutes: 60, paid: false,
    start: '2025-11-25', end: '2027-11-24', reviews: 121, tags: { '체험': 2, '미디어·사운드': 1, '역사·문화': 2 },
    fee: '무료', hours: 'both', entry: null },
  '식객: 맛의 뒤편': { short: '식객', building: '어린이문화원', minutes: 45, paid: true,
    start: '2026-09-17', end: '2026-12-13', reviews: 89, tags: { '체험': 1, '미디어·사운드': 1 },
    fee: '유료: 일반 10,000원 · 만 6세 이하 무료 (광주·전남 8,000원, 문화패스·문화누리·경로 5,000원, 중복 할인 없음)',
    hours: 'day', entry: '17:30' },
  '몬순으로 열린 세계': { short: '몬순', building: '아시아문화박물관', minutes: 45, paid: false,
    start: '2024-01-30', end: '2026-12-31', reviews: 73, tags: { '체험': 2, '역사·문화': 2 },
    fee: '무료', hours: 'both', entry: null, closed: ['2026-11-30', '2026-12-09'] },
  '2026 ACC 미래상: 김영은': { short: '김영은', building: '문화창조원', minutes: 60, paid: false,
    start: '2026-09-03', end: '2027-02-14', reviews: 64, tags: { '미디어·사운드': 2 },
    fee: '무료', hours: 'both', entry: null, note: '60분 순환 재생, 매시 정각 시작' },
  '미틸라 회화': { short: '미틸라', building: '아시아문화박물관', minutes: 20, paid: false,
    start: '2026-08-20', end: '2027-02-28', reviews: 30, tags: { '명화·그림': 1, '체험': 1, '역사·문화': 1 },
    fee: '무료', hours: 'both', entry: null },
  '사변적 공동체': { short: '사변적 공동체', building: '문화창조원', minutes: 40, paid: false,
    start: '2026-11-19', end: '2026-11-29', reviews: 8, tags: { '체험': 1, '미디어·사운드': 1 },
    fee: '무료', hours: 'both', entry: null }
};
// 빛의 숲·어린이체험관은 추천 대상에서 뺌 (시뮬레이션 0-1)

// 키워드 비율 % (ACC현재전시_대표키워드.md 3번). 순서: 위 EXHIBITIONS 순서와 같음
var EX_ORDER = Object.keys(EXHIBITIONS);
var RATIO = {
  '아이 동반':     [31, 38, 35, 36, 20, 30, 50],
  '데이트·커플':   [31, 23, 44, 20, 40, 23, 25],
  '친구와':        [9, 7, 11, 4, 0, 6, 12],
  '혼자':          [5, 2, 6, 5, 0, 3, 0],
  '체험형':        [54, 72, 46, 57, 14, 46, 50],
  '사진·포토존':   [40, 21, 12, 9, 3, 3, 0],
  '몰입·미디어아트': [41, 45, 34, 28, 34, 23, 25],
  '해설·도슨트':   [32, 6, 3, 6, 6, 0, 0],
  '읽을거리·설명': [36, 23, 20, 10, 14, 20, 12],
  '굿즈·쇼핑':     [40, 9, 35, 6, 6, 6, 0],
  '명화·원화':     [55, 0, 17, 1, 18, 3, 0],
  '역사·문명 교류': [14, 90, 7, 84, 21, 26, 0],
  '현대미술·기술': [18, 21, 25, 16, 98, 46, 100],
  '예약 필요':     [48, 6, 30, 5, 9, 6, 37],
  '붐빔':          [11, 6, 2, 2, 3, 3, 0],
  '쉬움·입문':     [21, 10, 17, 2, 4, 10, 25],
  '어려움·난해':   [8, 4, 3, 1, 6, 0, 12],
  '힐링·여유':     [24, 33, 11, 20, 14, 16, 12],
  '재미·신기':     [37, 57, 41, 54, 15, 16, 50]
};

// 동행 가중치 (1-1의 1번)
var COMPANION_WEIGHT = {
  '가족': { '아이 동반': 2, '체험형': 2, '사진·포토존': 0.5, '재미·신기': 0.5, '어려움·난해': -1 },
  '커플·친구': { '데이트·커플': 1.5, '친구와': 1, '사진·포토존': 1, '굿즈·쇼핑': 0.5, '명화·원화': 0.5,
    '현대미술·기술': 0.5, '몰입·미디어아트': 0.5, '아이 동반': -0.5 },
  '혼자': { '혼자': 1, '해설·도슨트': 0.5, '읽을거리·설명': 0.5, '명화·원화': 0.5, '현대미술·기술': 0.5,
    '역사·문명 교류': 0.5, '힐링·여유': 0.5, '아이 동반': -0.5 }
};
var VISITOR_ADJ = { '쉬움·입문': 1, '명화·원화': 0.5, '붐빔': -1, '예약 필요': -0.5 }; // 외지 조정 (2번)
var TASTE_KEYS = ['명화·원화', '현대미술·기술', '몰입·미디어아트', '해설·도슨트', '읽을거리·설명', '역사·문명 교류', '굿즈·쇼핑'];
var INTRO_ADD = { '재미·신기': 1, '체험형': 1, '쉬움·입문': 1, '어려움·난해': -2 }; // 입문 모드 (1-4)

var TOPICS = ['명화·그림', '체험', '미디어·사운드', '역사·문화'];
var SHRINK = 30, TAG_REP = 50, TAG_SUB = 20, NEW_BONUS = 20, END_BONUS = 20, WINDOW_DAYS = 28, MOVE_MIN = 10;

// ───────────── 2. 추천 계산 ─────────────
function ratio_(name, key) { return (RATIO[key] ? RATIO[key][EX_ORDER.indexOf(name)] : 0) / 100; }
function day_(s) { var p = s.split('-'); return new Date(+p[0], +p[1] - 1, +p[2]); }
function diffDays_(a, b) { return Math.round((a - b) / 86400000); }
function md_(s) { var p = s.split('-'); return (+p[1]) + '/' + (+p[2]); }

function companionMatch_(comp, name) {
  if (comp === '가족') return ratio_(name, '아이 동반') >= 0.3;
  if (comp === '커플·친구') return ratio_(name, '데이트·커플') + ratio_(name, '친구와') >= 0.3;
  return true; // 혼자는 항상 맞음
}

/**
 * @param a {companion, minutes(60|120), topics[], paidOk(bool), newPref(bool), date(Date)}
 * @return {route[], totalMin, scores{}, reasons{}, intro}
 */
function recommend(a) {
  var today = new Date(a.date.getFullYear(), a.date.getMonth(), a.date.getDate());
  var topics = a.topics || [];
  var intro = topics.length === 0;
  var paidOk = intro ? false : a.paidOk;
  var newPref = intro ? true : a.newPref;

  // 필터 (1-3의 5·6번)
  var cands = EX_ORDER.filter(function (n) {
    var x = EXHIBITIONS[n];
    if (today > day_(x.end) || today < day_(x.start)) return false;
    if (x.closed && today >= day_(x.closed[0]) && today <= day_(x.closed[1])) return false;
    if (x.paid && !paidOk) return false;
    return true;
  });
  if (!cands.length) return { route: [], totalMin: 0, scores: {}, reasons: {}, intro: intro };

  // 동행 점수 + 외지 조정 (1·2번)
  var w = {}, k;
  for (k in COMPANION_WEIGHT[a.companion]) w[k] = COMPANION_WEIGHT[a.companion][k];
  if (intro) {
    TASTE_KEYS.forEach(function (t) { delete w[t]; });
    for (k in INTRO_ADD) w[k] = (w[k] || 0) + INTRO_ADD[k];
  }
  var raw = {}, sum = 0;
  cands.forEach(function (n) {
    var s = 0, kk;
    for (kk in w) s += ratio_(n, kk) * w[kk];
    for (kk in VISITOR_ADJ) s += ratio_(n, kk) * VISITOR_ADJ[kk];
    raw[n] = s; sum += s;
  });
  // 소표본 보정 → 1등 = 100점 (3번)
  var mean = sum / cands.length, shr = {}, mx = -Infinity;
  cands.forEach(function (n) {
    var r = EXHIBITIONS[n].reviews;
    shr[n] = (r * raw[n] + SHRINK * mean) / (r + SHRINK);
    if (shr[n] > mx) mx = shr[n];
  });

  // 주제·새 전시·곧 끝나는 전시 가점, 일치 수 (4~6번, 1-2)
  var score = {}, match = {}, reasons = {}, flag = {};
  cands.forEach(function (n) {
    var x = EXHIBITIONS[n], s = shr[n] / mx * 100, m = 0, why = [];
    topics.forEach(function (t) {
      var g = x.tags[t] || 0;
      if (g === 2) { s += TAG_REP; m += 2; why.push('관심 주제 ' + t + ' 대표 전시'); }
      else if (g === 1) { s += TAG_SUB; m += 1; why.push('관심 주제 ' + t + ' 관련'); }
    });
    if (companionMatch_(a.companion, n)) {
      m += 1;
      if (a.companion === '가족') why.push('아이 동반 후기 ' + Math.round(ratio_(n, '아이 동반') * 100) + '%');
      if (a.companion === '커플·친구') why.push('데이트·친구 후기 ' + Math.round((ratio_(n, '데이트·커플') + ratio_(n, '친구와')) * 100) + '%');
    }
    if (intro) why.push('쉬운 전시 (후기 체험형 ' + Math.round(ratio_(n, '체험형') * 100) + '% · 재미·신기 ' +
        Math.round(ratio_(n, '재미·신기') * 100) + '% · 어려움 ' + Math.round(ratio_(n, '어려움·난해') * 100) + '%)');
    var isNew = diffDays_(today, day_(x.start)) < WINDOW_DAYS;
    var ending = diffDays_(day_(x.end), today) < WINDOW_DAYS;
    if (isNew && newPref) { s += NEW_BONUS; m += 1; why.push('새 전시 (' + md_(x.start) + ' 시작)'); flag[n] = 1; }
    else if (ending) { s += END_BONUS; m += 1; why.push(md_(x.end) + ' 종료, 놓치기 전에'); flag[n] = 1; }
    score[n] = s; match[n] = m; reasons[n] = why;
  });

  // 1등과 후보 (1-3, 1-4)
  var first, pool;
  var byScore = function (p, n) { return score[n] > score[p] ? n : p; };
  if (intro) {
    first = cands.reduce(byScore);
    pool = cands.filter(function (n) {
      if (n === first || !companionMatch_(a.companion, n)) return false;
      var hasRep = Object.keys(EXHIBITIONS[n].tags).some(function (t) { return EXHIBITIONS[n].tags[t] === 2; });
      return hasRep || flag[n];
    });
  } else {
    var tagged = cands.filter(function (n) { return topics.some(function (t) { return EXHIBITIONS[n].tags[t]; }); });
    if (!tagged.length) return { route: [], totalMin: 0, scores: score, reasons: reasons, intro: intro };
    first = tagged.reduce(byScore);
    pool = cands.filter(function (n) { return n !== first && match[n] >= 2; });
  }

  // 동선 (60분 = 1개, 120분 = 시간 안에서 점수 합 최대, 최대 3개)
  var best = { total: score[first], seq: [first] };
  if (+a.minutes === 120) {
    combos_(pool, 2).forEach(function (c) {
      if (!c.length) return;
      var seq = order_([first].concat(c));
      if (routeMin_(seq) > 120) return;
      var tot = seq.reduce(function (t, n) { return t + score[n]; }, 0);
      if (tot > best.total) best = { total: tot, seq: seq };
    });
  }
  return { route: best.seq, totalMin: routeMin_(best.seq), scores: score, reasons: reasons, intro: intro };
}

function combos_(arr, maxK) {
  var out = [[]];
  arr.forEach(function (x) {
    out.slice().forEach(function (c) { if (c.length < maxK) out.push(c.concat([x])); });
  });
  return out;
}
function order_(seq) { // 1등 먼저, 같은 건물 이어서
  var b = EXHIBITIONS[seq[0]].building;
  var same = seq.slice(1).filter(function (n) { return EXHIBITIONS[n].building === b; });
  var other = seq.slice(1).filter(function (n) { return EXHIBITIONS[n].building !== b; });
  other.sort(function (p, q) { return EXHIBITIONS[p].building < EXHIBITIONS[q].building ? -1 : 1; });
  return [seq[0]].concat(same, other);
}
function routeMin_(seq) {
  var t = 0;
  seq.forEach(function (n, i) {
    t += EXHIBITIONS[n].minutes;
    if (i && EXHIBITIONS[n].building !== EXHIBITIONS[seq[i - 1]].building) t += MOVE_MIN;
  });
  return t;
}

// 1인 관람료 합계 (성인 기준) + 통합관람권 안내 (1-3의 7번, 식객 종료 12/13까지)
var ADULT_FEE = { '인상파, 찬란한 순간들': 13000, '식객: 맛의 뒤편': 10000 };
var COMBO = { a: '인상파, 찬란한 순간들', b: '식객: 맛의 뒤편', price: 15000 };
function won_(n) { return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '원'; }
function feeText_(route, d) {
  var sum = 0;
  route.forEach(function (n) { sum += ADULT_FEE[n] || 0; });
  var txt = '1인 합계(성인): ' + (sum ? won_(sum) : '무료');
  if (route.indexOf(COMBO.a) >= 0 && route.indexOf(COMBO.b) >= 0 && d <= new Date(2026, 11, 13, 23, 59)) {
    txt += '\n→ 통합관람권 ' + won_(COMBO.price) + ' (따로 사면 ' + won_(sum) + '), 어린이문화원 식객 매표소에서 구매';
  }
  return txt;
}

// 운영시간·입장 마감 (그날 요일 기준)
function hoursText_(name, d) {
  var x = EXHIBITIONS[name], wd = d.getDay(); // 0=일
  if (wd === 1 || (d.getMonth() === 0 && d.getDate() === 1)) return '휴관일';
  var late = x.hours === 'both' && (wd === 3 || wd === 6);
  var close = late ? '20:00' : '18:00';
  var entry = x.entry === 'minus60' ? (late ? '19:00' : '17:00') : x.entry;
  return '10:00~' + close + (entry ? ' (입장 마감 ' + entry + ')' : '') + (x.note ? ' · ' + x.note : '');
}

// ───────────── 3. 웹앱 (설문 화면·응답 저장) ─────────────
var TZ = 'Asia/Seoul';
var SHEET_HEADERS = ['타임스탬프', '성별', '동행', '관람 시간', '관심 주제', '유료 전시', '새 전시 선호',
  '기준일', '추천 동선', '총 시간(분)', '추천 이유', '요금', '운영시간·입장 마감', '전시별 점수',
  '추천 전시 맘에 듦', 'ACC 방문 의향', '기타 의견'];

function doGet() {
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('ACC 전시 추천 설문')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1');
}

// 응답 시트 만들기 (한 번만 실행)
function setupSheet() {
  var props = PropertiesService.getScriptProperties();
  var id = props.getProperty('SHEET_ID');
  var ss = id ? SpreadsheetApp.openById(id) : SpreadsheetApp.create('ACC 전시 추천 설문 (응답)');
  props.setProperty('SHEET_ID', ss.getId());
  var sh = ss.getSheets()[0];
  sh.setName('응답');
  sh.getRange(1, 1, 1, SHEET_HEADERS.length).setValues([SHEET_HEADERS]).setFontWeight('bold').setBackground('#fff2cc');
  sh.setFrozenRows(1);
  Logger.log('응답 시트: ' + ss.getUrl());
  return ss.getUrl();
}

function todaySeoul_() {
  var s = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd').split('-');
  return new Date(+s[0], +s[1] - 1, +s[2], 12);
}

function toInput_(a) {
  var topics = (a.topics || []).filter(function (t) { return TOPICS.indexOf(t) >= 0; }).slice(0, 2);
  return {
    companion: COMPANION_WEIGHT[a.companion] ? a.companion : '혼자',
    minutes: +a.minutes === 120 ? 120 : 60,
    topics: topics,
    paidOk: a.paid !== '무료만',
    newPref: a.newPref !== '아니오',
    date: todaySeoul_()
  };
}

// 결과보기: 화면에서 부름 → 추천 전시 이름 순서·이유·요금 문구
function getRecommendation(a) {
  var inp = toInput_(a), res = recommend(inp);
  return {
    date: Utilities.formatDate(inp.date, TZ, 'yyyy-MM-dd'),
    route: res.route,
    totalMin: res.totalMin,
    intro: res.intro,
    reasons: res.route.map(function (n, i) { return (i === 0 ? '1등 · ' : '') + res.reasons[n].join(', '); }),
    fee: res.route.length ? feeText_(res.route, inp.date) : ''
  };
}

// 제출: 서버에서 추천을 다시 계산해 한 줄로 저장
function submitResponse(a) {
  if (['남', '여'].indexOf(a.gender) < 0) throw new Error('성별을 골라 주세요');
  if (['예', '아니요'].indexOf(a.like) < 0 || ['예', '아니요'].indexOf(a.visit) < 0) throw new Error('추가 질문에 답해 주세요');
  var inp = toInput_(a), res = recommend(inp), d = inp.date;
  var out = resultCells_(res, d);
  var sh = SpreadsheetApp.openById(PropertiesService.getScriptProperties().getProperty('SHEET_ID')).getSheets()[0];
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    sh.appendRow([new Date(), a.gender, inp.companion, inp.minutes + '분', inp.topics.join(', '),
      a.paid === '무료만' ? '무료만' : '괜찮음', a.newPref === '아니오' ? '아니오' : '예']
      .concat(out, [a.like, a.visit, String(a.comment || '').slice(0, 2000)]));
  } finally { lock.releaseLock(); }
  return true;
}

function resultCells_(res, d) {
  if (!res.route.length) return [fmtDate_(d), '추천 전시 없음', 0, '조건에 맞는 전시가 없음', '', '', ''];
  var lines = function (fn) { return res.route.map(function (n, i) { return (i + 1) + '. ' + EXHIBITIONS[n].short + ': ' + fn(n); }).join('\n'); };
  return [
    fmtDate_(d),
    res.route.map(function (n) { return EXHIBITIONS[n].short + ' (' + EXHIBITIONS[n].building + ', ' + EXHIBITIONS[n].minutes + '분)'; }).join(' → '),
    res.totalMin,
    (res.intro ? '[관심 주제 안 고름: 입문 모드, 무료만·새 전시 예로 계산]\n' : '') +
      lines(function (n) { return (n === res.route[0] ? '1등 · ' : '') + (res.reasons[n].join(', ') || '점수 상위'); }),
    feeText_(res.route, d) + '\n' + lines(function (n) { return EXHIBITIONS[n].fee; }),
    lines(function (n) { return hoursText_(n, d); }),
    Object.keys(res.scores).sort(function (p, q) { return res.scores[q] - res.scores[p]; })
      .map(function (n) { return EXHIBITIONS[n].short + ' ' + res.scores[n].toFixed(1); }).join(', ')
  ];
}

function fmtDate_(d) {
  var w = ['일', '월', '화', '수', '목', '금', '토'][d.getDay()];
  return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2) + ' (' + w + ')';
}
