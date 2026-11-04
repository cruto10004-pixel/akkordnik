const sharp=require('/vercel/sandbox/node_modules/sharp/dist/index.cjs');const fs=require('fs');const R='/data/akkordnik-repo/android/app/src/main/res/';
const d={mdpi:1,hdpi:1.5,xhdpi:2,xxhdpi:3,xxxhdpi:4};
(async()=>{const fg=fs.readFileSync(__dirname+'/fg.svg'),bg=fs.readFileSync(__dirname+'/bg.svg');
 for(const [k,m] of Object.entries(d)){const F=Math.round(108*m),L=Math.round(48*m);
  await sharp(fg,{density:72*m*4}).resize(F,F).png().toFile(R+`mipmap-${k}/ic_launcher_foreground.png`);
  const big=await sharp(bg,{density:300}).resize(F,F).composite([{input:await sharp(fg,{density:300}).resize(F,F).png().toBuffer()}]).png().toBuffer();
  const crop=Math.round(F*0.1667); // legacy: visible 72/108 region roughly
  const sq=await sharp(big).extract({left:Math.round(F*0.125),top:Math.round(F*0.125),width:Math.round(F*0.75),height:Math.round(F*0.75)}).resize(L,L).png().toBuffer();
  const r=L*0.22; const mask=Buffer.from(`<svg width="${L}" height="${L}"><rect width="${L}" height="${L}" rx="${r}" fill="#fff"/></svg>`);
  await sharp(sq).composite([{input:mask,blend:'dest-in'}]).png().toFile(R+`mipmap-${k}/ic_launcher.png`);
  const cm=Buffer.from(`<svg width="${L}" height="${L}"><circle cx="${L/2}" cy="${L/2}" r="${L/2}" fill="#fff"/></svg>`);
  await sharp(sq).composite([{input:cm,blend:'dest-in'}]).png().toFile(R+`mipmap-${k}/ic_launcher_round.png`);
 }
 await sharp(R+'mipmap-xxxhdpi/ic_launcher.png').resize(512,512).toFile('/data/akkordnik-repo/art/icon-preview.png');
 // web icon too
 await sharp(R+'mipmap-xxxhdpi/ic_launcher.png').toFile('/data/akkordnik-repo/www/icon.png');
 process.exit(0)})();
