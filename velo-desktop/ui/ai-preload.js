// Runs in the assistant panel's pages before their own code. The panel presents itself as Firefox
// (Google refuses sign-in from embedded Chromium), so the Chromium-only hints are hidden to match.
const { webFrame } = require('electron');
webFrame.executeJavaScript(
  "try{Object.defineProperty(Navigator.prototype,'userAgentData',{get(){return undefined},configurable:true});" +
  "Object.defineProperty(Navigator.prototype,'vendor',{get(){return ''},configurable:true});delete window.chrome;}catch(e){}"
);
