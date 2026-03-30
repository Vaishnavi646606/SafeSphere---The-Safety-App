import type { Config } from 'tailwindcss'

const config: Config = {
  content: ['./src/**/*.{js,ts,jsx,tsx,mdx}'],
  theme: {
    extend: {
      colors: {
        surface: {
          DEFAULT: '#111219',
          dark: '#0c0d13',
          input: '#16171f'
        },
        page: '#08090e'
      }
    }
  },
  plugins: []
}

export default config
