import type { Account, PostRow } from './store'
import { coverForNote, FEED_CATS, videoForNote } from './media'

type AuthorFn = (u: Account) => PostRow['author']

function ago(hours: number) {
  return new Date(Date.now() - hours * 3600_000).toISOString()
}

const IMAGE_TITLES = [
  ['海边把鞋子脱了走', '沙是温的。风把头发吹到嘴里，我也不在意。', '旅行'],
  ['清晨的第一杯美式', '店员记得少冰。窗外在下雨，玻璃上全是叶子。', '生活'],
  ['市集淘到的旧相机', '快门还有声音。胶卷要下周才洗得出来。', '摄影'],
  ['一碗拉面刚好热', '汤面上的油花在转。筷子先碰蛋黄。', '美食'],
  ['这件衬衫穿了一夏天', '领口洗旧了，反而好看。', '时尚'],
  ['山路十八弯之后', '云在脚下。手机信号没有，心情很好。', '旅行'],
  ['窗台的多肉又长了一圈', '浇水总是犹豫，它自己活得很好。', '生活'],
  ['夜跑过江桥', '灯一条一条亮。配速一般，步数够了。', '运动'],
  ['唱片店角落的下午', '老板在擦黑胶。我站着听完一面。', '音乐'],
  ['排骨炖到软', '汤要隔夜才好吃。今晚先喝一勺。', '美食'],
  ['雨后的石板路', '倒影里有伞，也有树。', '摄影'],
  ['旧货店里的大衣', '口袋里还留着一张电影票。', '时尚'],
  ['机场延误的两小时', '看飞机起落，把这一页写完。', '旅行'],
  ['阳台晒到的被子', '有太阳的味道。晚上会睡得很沉。', '生活'],
  ['公园的第一场球', '鞋湿了。下次带双备用。', '运动'],
  ['吉他换了新弦', '前三天手会疼，第五天就顺了。', '音乐'],
  ['火锅只点毛肚', '朋友笑我。下次还是这样。', '美食'],
  ['山顶的风比想象大', '帽子差点飞走。云却很近。', '旅行'],
  ['旧巷口的灯', '黄得很均匀。适合慢慢走。', '摄影'],
  ['针织帽和红围巾', '镜子前试了三次，还是这一套。', '时尚'],
  ['厨房里的烟', '葱花下去的那一秒最香。', '美食'],
  ['湖边骑了一圈', '腿有点酸。水很平。', '运动'],
  ['书店二楼靠窗', '把想买的书拍下来，最后只带走一本。', '生活'],
  ['巷子里的猫', '不让摸。允许拍照。', '摄影'],
  ['周末的第一趟高铁', '窗外是田。耳机里是旧歌。', '旅行'],
  ['把书桌收拾干净', '只留一杯水和一支笔。', '生活'],
  ['球场空了之后', '回声还在。我多投了十个。', '运动'],
  ['直播间外的排练', '调了半小时音，还是差一点点。', '音乐'],
  ['糖醋排骨的糖色', '火候对了会亮。今晚对了。', '美食'],
  ['风衣扣子掉了一颗', '先别缝。这样也像故意的。', '时尚'],
  ['雾里的桥', '看不清对岸，反而想走过去。', '旅行'],
  ['午后的长影', '地板被切成一条一条。', '摄影'],
  ['草莓要洗三遍', '蒂留下，糖少放。', '美食'],
  ['新鞋第一次出门', '走的是熟悉的那条路。', '时尚'],
  ['海边的风筝', '线放得很长。人还在原地。', '生活'],
  ['夜市的鱿鱼', '烟比人高。排队值得。', '美食'],
  ['林间的步道', '松针铺了一层。脚步变轻。', '运动'],
  ['排练室的镜子', '动作还是歪的。再来一遍。', '音乐'],
  ['老街的午后', '茶是热的。时间是慢的。', '旅行'],
  ['花店门口站一会儿', '不买。闻一下就走。', '生活'],
  ['天台看成片的云', '城市在下面发出声音。', '摄影'],
  ['第一条牛仔裤', '洗过之后更像自己的。', '时尚'],
  ['清晨的菜市场', '鱼还在跳。葱是带泥的。', '生活'],
  ['河对岸的灯火', '风一吹就碎。', '摄影'],
  ['登山到一半的水', '比预想的渴。风景比预想的好。', '运动'],
  ['把耳机分给旁边的人', '同一首歌，两个人不说话。', '音乐'],
  ['夜班车最后一排', '城市变成一条光带。', '旅行'],
  ['第一次自己做面包', '外形一般，味道过关。', '美食'],
  ['雨衣和胶鞋', '水坑可以踩。', '生活'],
  ['黄昏把相机放下', '眼睛比镜头先记住。', '摄影'],
] as const

const VIDEO_TITLES = [
  ['江边骑行一段', '风声比配乐好听。中途停下来看船。', '运动'],
  ['把日落拍成二十秒', '云走得很快。灯一盏盏亮。', '摄影'],
  ['厨房里的三分钟面', '水开再下。蛋要嫩。', '美食'],
  ['排练室走位', '镜面墙还是会走神。', '音乐'],
  ['山路下坡', '刹车要早。风景在左边。', '旅行'],
  ['街头随拍', '人不看镜头才好看。', '摄影'],
  ['夜跑配速测试', '最后一公里才热起来。', '运动'],
  ['把这首弹完', '中间错了一个音，没有重来。', '音乐'],
  ['逛市集一圈', '买了杯热可可，别的先看看。', '生活'],
  ['车窗里的城市', '雨刮器打拍子。', '旅行'],
  ['第一次滑板', '摔了两次，第三次能走两米。', '运动'],
  ['咖啡拉花失败', '喝起来是好的。', '美食'],
  ['天台看飞机', '声音比灯先到。', '生活'],
  ['把房间整理完', '最后拍一张空书桌。', '生活'],
  ['海边赤脚跑', '浪来了就停。', '运动'],
  ['夜市吃到撑', '最后还是点了烤年糕。', '美食'],
  ['巷口的一场雨', '大家往屋檐下站。', '摄影'],
  ['把旧歌翻唱一句', '原调太高，降了半音。', '音乐'],
  ['树林里骑行', '落叶比路厚。', '运动'],
  ['黄昏的菜市场收摊', '灯光一盏盏关。', '生活'],
  ['站台上的两分钟', '车来了，风也来了。', '旅行'],
  ['把这盘菜翻面', '焦香刚刚好。', '美食'],
  ['楼顶练投篮', '网是破的，手感还在。', '运动'],
  ['录音棚外走廊', '门缝里漏出鼓点。', '音乐'],
  ['雪后的院子', '第一步要小心。', '生活'],
  ['把相机对准猫', '它转过头去。', '摄影'],
  ['夜航路过灯火', '城市像电路板。', '旅行'],
  ['周末的一场球', '最后进的那个最开心。', '运动'],
  ['把汤舀起来', '热气挡住镜头。', '美食'],
  ['排练结束鼓掌', '自己给自己拍。', '音乐'],
  ['海边的风太大', '帽子在手里，不戴了。', '旅行'],
  ['把花插进瓶里', '高低差试了三次。', '生活'],
  ['街角的滑板少年', '镜头跟了两秒就停。', '摄影'],
  ['夜跑过桥洞', '回声把呼吸放大。', '运动'],
  ['把这首听到副歌', '副歌比主歌更像夏天。', '音乐'],
  ['山里的雾散开', '路突然出现。', '旅行'],
  ['把饺子下锅', '浮起来再捞。', '美食'],
  ['天黑之前到家', '楼道灯是声控的。', '生活'],
  ['把焦段拉近', '人还不知道被拍。', '摄影'],
  ['球场空了以后', '多投十个再走。', '运动'],
  ['把音响打开', '邻居还没回家。', '音乐'],
  ['渡轮离开码头', '城市慢慢变小。', '旅行'],
  ['把面包切开', '气孔还行。', '美食'],
  ['下雨天的车窗', '水珠往斜后方走。', '生活'],
  ['把晚霞接到最后', '紫比红更短。', '摄影'],
  ['坡度有点陡', '下来比上去难。', '运动'],
  ['把这段过门弹顺', '明天再录人声。', '音乐'],
  ['夜市收摊的烟', '鱿鱼的味道还在。', '美食'],
  ['把脚架支好', '风还是会晃一点点。', '摄影'],
  ['最后一公里加速', '表盘亮着，人已经空了。', '运动'],
] as const

const LIVE_TITLES: [string, string, string][] = [
  ['在大阪找立食', '生活分享', '今天只吃饭，问店名打公屏。'],
  ['排位陪跑', '游戏', '麦开着。骂人会被禁言。'],
  ['深夜食堂', '生活分享', '煮面给你们看，不带货。'],
  ['街拍一小时', '兴趣爱好', '遇到好看的光就停。'],
  ['晨跑连麦', '运动户外', '配速说话，不求快。'],
  ['客厅小音乐会', '才艺直播', '弹三首就下。'],
  ['整理书桌', '生活分享', '边收边说话。'],
  ['山里信号不好', '运动户外', '断了就等一下。'],
  ['厨房做一人食', '生活分享', '失败了也播。'],
  ['新歌试听', '才艺直播', '耳机建议戴上。'],
  ['城市夜骑', '运动户外', '注意车灯。'],
  ['博物馆随便逛', '兴趣爱好', '轻声。'],
  ['把旧相机修好', '科技新闻', '拆开再装回去。'],
  ['海边看日落', '生活分享', '风大，麦会噪。'],
  ['宅家打本', '游戏', '缺一个输出。'],
  ['咖啡拉花练习', '生活分享', '失败镜头不剪。'],
  ['公园写生', '兴趣爱好', '人多就换地方。'],
  ['夜市探店', '生活分享', '排队也播。'],
  ['健身房徒手', '运动户外', '跟不跟随便。'],
  ['黑胶试听会', '才艺直播', '一面一张。'],
  ['雨天室内骑行', '运动户外', '功率随便看。'],
  ['把房间刷白', '生活分享', '味道有点冲。'],
  ['星空等云散', '兴趣爱好', '等得久就聊天。'],
  ['手工皮件', '兴趣爱好', '手慢可以倍速。'],
  ['开车去下一站', '生活分享', '不看手机。'],
  ['把这首练熟', '才艺直播', '错了重来。'],
  ['菜市场买菜', '生活分享', '问价可以扣1。'],
  ['河边放空', '生活分享', '今天没什么流程。'],
  ['双排上分', '游戏', '心态要稳。'],
  ['把花园浇了', '生活分享', '蚊子很多。'],
  ['摄影问答', '兴趣爱好', '参数都可以问。'],
  ['夜跑直播', '运动户外', '注意安全。'],
  ['把旧衣服改了', '兴趣爱好', '针脚不整齐。'],
  ['车站等车', '生活分享', '晚点了。'],
  ['把汤熬上', '生活分享', '中间会很闲。'],
  ['天台看飞机', '兴趣爱好', '声音会突然很大。'],
  ['把这首翻唱完', '才艺直播', '原调降了。'],
  ['周末市集', '生活分享', '人挤人。'],
  ['河边野餐', '生活分享', '蚂蚁先到。'],
  ['把键盘拆了', '科技新闻', '轴体有点脏。'],
  ['清晨钓鱼', '运动户外', '可能空军。'],
  ['把花束拆开', '兴趣爱好', '重新插一次。'],
  ['午夜电台', '才艺直播', '说话比唱歌多。'],
  ['把鞋刷干净', '生活分享', '边刷边聊。'],
  ['山顶看云海', '运动户外', '风大抱紧。'],
  ['把这首听完', '才艺直播', '副歌一起哼。'],
  ['夜班车最后一班', '生活分享', '座位很空。'],
  ['把房间灯光调好', '生活分享', '暖光还是冷光。'],
  ['把这局打完', '游戏', '可能要很久。'],
  ['把今天记下', '生活分享', '想到什么说什么。'],
]

export function extraImagePosts(authors: Account[], toAuthor: AuthorFn, startIndex: number): PostRow[] {
  return IMAGE_TITLES.map((item, i) => {
    const [title, body, category] = item
    const author = authors[(startIndex + i) % authors.length]
    const cover = coverForNote(title, body, category, `p_img_${i + 1}`)
    const n = 20 + ((i * 97) % 1800)
    return makePost({
      postId: `p_img_${i + 1}`,
      author,
      toAuthor,
      title,
      body,
      category,
      postType: 'image',
      cover,
      mediaUrl: cover,
      mime: 'image/jpeg',
      hours: 2 + i * 3,
      likes: Math.floor(n / 4),
      fav: Math.floor(n / 10),
      views: n,
    })
  })
}

export function extraVideoPosts(authors: Account[], toAuthor: AuthorFn, startIndex: number): PostRow[] {
  return VIDEO_TITLES.map((item, i) => {
    const [title, body, category] = item
    const author = authors[(startIndex + i) % authors.length]
    const cover = coverForNote(title, body, category, `p_vid_cover_${i + 1}`)
    const n = 40 + ((i * 131) % 2400)
    return makePost({
      postId: `p_vid_${i + 1}`,
      author,
      toAuthor,
      title,
      body,
      category,
      postType: 'video',
      cover,
      mediaUrl: videoForNote(title, body, category, `p_vid_${i + 1}`),
      mime: 'video/mp4',
      hours: 1 + i * 2.5,
      likes: Math.floor(n / 5),
      fav: Math.floor(n / 12),
      views: n,
    })
  })
}

export function extraLives(authors: Account[], toAuthor: AuthorFn) {
  const hosts = authors.filter((u) => u.liveHost)
  const pool = hosts.length ? hosts : authors
  return LIVE_TITLES.map((item, i) => {
    const [title, category, notice] = item
    const owner = pool[i % pool.length]
    return {
      sessionId: `lv_${i + 1}`,
      roomId: `rm_${i + 1}`,
      title,
      category,
      status: 'live',
      coverUrl: coverForNote(title, notice, category, `lv_${i + 1}`),
      notice,
      playUrlHls: videoForNote(title, notice, category, `lv_${i + 1}`),
      viewerCount: 40 + ((i * 73) % 2200),
      owner: toAuthor(owner),
      startedAt: ago(0.3 + (i % 8) * 0.4),
      ended: false,
    }
  })
}

function makePost(opts: {
  postId: string
  author: Account
  toAuthor: AuthorFn
  title: string
  body: string
  category: string
  postType: string
  cover: string
  mediaUrl: string
  mime: string
  hours: number
  likes: number
  fav: number
  views: number
}): PostRow {
  const cat = FEED_CATS.includes(opts.category as (typeof FEED_CATS)[number]) ? opts.category : '生活'
  return {
    postId: opts.postId,
    authorId: opts.author.userId,
    author: opts.toAuthor(opts.author),
    title: opts.title,
    body: opts.body,
    coverUrl: opts.cover,
    postType: opts.postType,
    category: cat,
    original: true,
    contentKind: 'note',
    eventId: '',
    eventTitle: '',
    visibility: 'public',
    likeCount: opts.likes,
    favoriteCount: opts.fav,
    commentCount: 0,
    viewCount: opts.views,
    publishedAt: ago(opts.hours),
    reviewStatus: 'published',
    likedByMe: false,
    favoritedByMe: false,
    media: [
      { mediaId: `m_${opts.postId}`, url: opts.mediaUrl, mimeType: opts.mime, role: 'cover' },
    ],
    takenDown: false,
  }
}
