const word = process.argv[2] || '海边日落'
const w = encodeURIComponent(word)
const url = `https://image.baidu.com/search/acjson?tn=resultjson_com&ipn=rj&ct=201326592&fp=result&word=${w}&queryWord=${w}&pn=0&rn=8`
const r = await fetch(url, {
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0',
    Referer: 'https://image.baidu.com/',
  },
})
const t = await r.text()
const thumbs = [...t.matchAll(/"thumbURL":"([^"]+)"/g)].map((m) => m[1].replace(/\\u002F/g, '/'))
const objs = [...t.matchAll(/"objURL":"([^"]+)"/g)].map((m) => m[1].replace(/\\u002F/g, '/'))
console.log('status', r.status, 'thumb', thumbs.length, 'obj', objs.length)
for (const u of thumbs.slice(0, 3)) {
  try {
    const g = await fetch(u)
    const n = (await g.arrayBuffer()).byteLength
    console.log('OK', n, u.slice(0, 85))
  } catch (e) {
    console.log('ERR', u.slice(0, 60))
  }
}
