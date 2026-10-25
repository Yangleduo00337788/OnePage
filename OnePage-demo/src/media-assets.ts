/** 已校验可用的百度图链，按主题分组（勿放百科无效 ID / JS 徽标等） */
export const IMAGE_POOL: Record<string, readonly string[]> = {
  旅行: [
    'https://imgsrc.baidu.com/baike/pic/item/35a85edf8db1cb13043a4354d554564e92584b21.png',
    'https://imgsrc.baidu.com/baike/pic/item/0b46f21fbe096b637120d5760d338744ebf8acdf.jpg',
    'https://publish-pic-cpu.baidu.com/1a906c6e-0599-4225-a70b-7aba4d9956fe.jpeg',
    'https://publish-pic-cpu.baidu.com/0bbd608f-6c95-4a6b-8358-6a1c7c4957e2.jpeg',
    'https://publish-pic-cpu.baidu.com/cf826245-1f54-4967-82d8-b16acb773dde.jpeg',
  ],
  美食: [
    'https://publish-pic-cpu.baidu.com/1e9d9c6c-1ee1-4c97-b16f-d52fbc66d759.jpeg',
    'https://publish-pic-cpu.baidu.com/fecde7e7-f045-40a2-aac1-d607514b2919.jpeg',
    'https://imgsrc.baidu.com/baike/pic/item/0b46f21fbe096b637120d5760d338744ebf8acdf.jpg',
  ],
  时尚: [
    'https://imgsrc.baidu.com/baike/pic/item/91ef76c6a7efce1b27893518a451f3deb58f6546.jpg',
    'https://publish-pic-cpu.baidu.com/7c0f2679-dd57-489f-8d1d-86c67154d492.jpeg',
    'https://publish-pic-cpu.baidu.com/848dd415-797f-4e19-9f07-0e6a0b5cb5bf.jpeg',
  ],
  摄影: [
    'https://imgsrc.baidu.com/baike/pic/item/0b46f21fbe096b637120d5760d338744ebf8acdf.jpg',
    'https://publish-pic-cpu.baidu.com/5173a099-1e81-4b4e-b3e6-c82734f440f1.jpeg',
    'https://publish-pic-cpu.baidu.com/c0c92525-13ed-47c0-880a-3f2068e21010.jpeg',
    'https://imgsrc.baidu.com/baike/pic/item/35a85edf8db1cb13043a4354d554564e92584b21.png',
  ],
  生活: [
    'https://publish-pic-cpu.baidu.com/85c46cd1-c923-4aa4-864d-8c4ae4f83221.jpeg',
    'https://publish-pic-cpu.baidu.com/fecde7e7-f045-40a2-aac1-d607514b2919.jpeg',
    'https://publish-pic-cpu.baidu.com/7c0f2679-dd57-489f-8d1d-86c67154d492.jpeg',
    'https://imgsrc.baidu.com/baike/pic/item/91ef76c6a7efce1b27893518a451f3deb58f6546.jpg',
  ],
  运动: [
    'https://publish-pic-cpu.baidu.com/5173a099-1e81-4b4e-b3e6-c82734f440f1.jpeg',
    'https://publish-pic-cpu.baidu.com/cf826245-1f54-4967-82d8-b16acb773dde.jpeg',
    'https://publish-pic-cpu.baidu.com/1a906c6e-0599-4225-a70b-7aba4d9956fe.jpeg',
  ],
  游戏: [
    'https://publish-pic-cpu.baidu.com/c0c92525-13ed-47c0-880a-3f2068e21010.jpeg',
    'https://publish-pic-cpu.baidu.com/5173a099-1e81-4b4e-b3e6-c82734f440f1.jpeg',
    'https://publish-pic-cpu.baidu.com/85c46cd1-c923-4aa4-864d-8c4ae4f83221.jpeg',
  ],
  音乐: [
    'https://imgsrc.baidu.com/baike/pic/item/91ef76c6a7efce1b27893518a451f3deb58f6546.jpg',
    'https://publish-pic-cpu.baidu.com/c0c92525-13ed-47c0-880a-3f2068e21010.jpeg',
    'https://publish-pic-cpu.baidu.com/7c0f2679-dd57-489f-8d1d-86c67154d492.jpeg',
  ],
}

/** 好看样片：按内容主题分组 */
export const VIDEO_POOL: Record<string, readonly string[]> = {
  旅行: [
    'https://vd3.bdstatic.com/mda-imjxm02cjs8c17x5/mda-imjxm02cjs8c17x5.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
    'https://vd3.bdstatic.com/mda-nej5gku1wneiy6tg/hd/cae_h264/1653020671274026866/mda-nej5gku1wneiy6tg.mp4',
    'https://vd3.bdstatic.com/mda-imhpxuuemqvrvsm9/mda-imhpxuuemqvrvsm9.mp4?playlist=%5B%22hd%22%5D',
  ],
  美食: [
    'https://vd3.bdstatic.com/mda-im5cusaf4mpgg7kh/mda-im5cusaf4mpgg7kh.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
    'https://vd3.bdstatic.com/mda-im6m7i0uhidkreek/mda-im6m7i0uhidkreek.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
    'https://vd3.bdstatic.com/mda-ncfedvx0ein061t4/480p/h264_delogo/1647425564220598999/mda-ncfedvx0ein061t4.mp4',
  ],
  时尚: [
    'https://vd3.bdstatic.com/mda-imregnww86ky5emm/mda-imregnww86ky5emm.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
    'https://vd3.bdstatic.com/mda-ncfe4786mmz8ap4i/hd/cae_h264_delogo/1647425577273799775/mda-ncfe4786mmz8ap4i.mp4',
  ],
  摄影: [
    'https://vd3.bdstatic.com/mda-imhpxuuemqvrvsm9/mda-imhpxuuemqvrvsm9.mp4?playlist=%5B%22hd%22%5D',
    'https://vd3.bdstatic.com/mda-nej5gku1wneiy6tg/hd/cae_h264/1653020671274026866/mda-nej5gku1wneiy6tg.mp4',
  ],
  生活: [
    'https://vd3.bdstatic.com/mda-im6ry4epe5qix4de/mda-im6ry4epe5qix4de.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
    'https://vd3.bdstatic.com/mda-imhpxuuemqvrvsm9/mda-imhpxuuemqvrvsm9.mp4?playlist=%5B%22hd%22%5D',
    'https://vd3.bdstatic.com/mda-ijnp4qhtdkn6f075/mda-ijnp4qhtdkn6f075.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
  ],
  运动: [
    'https://vd3.bdstatic.com/mda-imvpa53ufpwtiqb2/mda-imvpa53ufpwtiqb2.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
    'https://vd3.bdstatic.com/mda-ndid6k6uyrrg858j/hd/cae_h264_delogo/1650360786418222931/mda-ndid6k6uyrrg858j.mp4',
    'https://vd3.bdstatic.com/mda-ndhd2nv54ihi4zt8/hd/cae_h264_delogo/1650273984010202369/mda-ndhd2nv54ihi4zt8.mp4',
  ],
  游戏: [
    'https://vd3.bdstatic.com/mda-ijnp4qhtdkn6f075/mda-ijnp4qhtdkn6f075.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
    'https://vd3.bdstatic.com/mda-im6ry4epe5qix4de/mda-im6ry4epe5qix4de.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
    'https://vd3.bdstatic.com/mda-ncpe351fr2d4shrc/hd/cae_h264_delogo/1648117010364799753/mda-ncpe351fr2d4shrc.mp4',
  ],
  音乐: [
    'https://vd3.bdstatic.com/mda-im6m7i0uhidkreek/mda-im6m7i0uhidkreek.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
    'https://vd3.bdstatic.com/mda-imvpa53ufpwtiqb2/mda-imvpa53ufpwtiqb2.mp4?playlist=%5B%22hd%22%2C%22sc%22%5D',
  ],
}

export const PORTRAIT_POOL = IMAGE_POOL.生活
export const BANNER_POOL = IMAGE_POOL.旅行
