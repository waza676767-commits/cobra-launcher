# Velo for Windows

The desktop version of Velo, built with Electron. Same ideas as the Android app: DuckDuckGo search,
tabs that are saved and offered back on the next start, ad blocker, private search, RAM limit that
sleeps background tabs, pinned pages, themes and wallpapers, and an assistant side panel that can
show two assistants at once.

```bash
npm install
npm start          # run it
npm run dist       # dist/Velo-<version>-win.zip (unzip anywhere and run Velo.exe)
```

Building a one-click installer (`electron-builder --win nsis`) has to be done on Windows, or on
Linux with Wine installed.
