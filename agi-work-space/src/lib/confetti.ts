import confetti from 'canvas-confetti'

export function firePushConfetti() {
  const end = Date.now() + 1600
  const colors = ['#f59e0b', '#ef4444', '#22c55e', '#3b82f6', '#a855f7']

  const frame = () => {
    confetti({
      particleCount: 4,
      angle: 60,
      spread: 55,
      origin: { x: 0, y: 0.7 },
      colors,
    })
    confetti({
      particleCount: 4,
      angle: 120,
      spread: 55,
      origin: { x: 1, y: 0.7 },
      colors,
    })
    if (Date.now() < end) requestAnimationFrame(frame)
  }

  confetti({
    particleCount: 120,
    spread: 80,
    origin: { y: 0.6 },
    colors,
  })
  frame()
}
