import { Buffer } from 'buffer'

const globalRef = globalThis as typeof globalThis & {
  Buffer?: typeof Buffer
  global?: typeof globalThis
}

globalRef.Buffer = Buffer
globalRef.global = globalThis
