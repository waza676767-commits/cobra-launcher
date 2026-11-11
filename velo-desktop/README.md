# Velo for Windows

The desktop version of Velo, built with Electron. Same ideas as the Android app: DuckDuckGo search,
tabs that are saved and offered back on the next start, ad blocker, private search, RAM limit that
sleeps background tabs, pinned pages, themes and wallpapers, and an assistant side panel that can
show two assistants at once.

```bash
npm install
npm start          # run it
npm run dist       # dist/Velo-Setup-<version>.exe
```

The installer adds Velo to the Start menu and desktop with its own icon. Building it on Linux needs
Wine; on Windows it works as is.
