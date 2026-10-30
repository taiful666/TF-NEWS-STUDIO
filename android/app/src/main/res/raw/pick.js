(function(){
  function m(n){var e=document.querySelector('meta[property="'+n+'"],meta[name="'+n+'"],meta[itemprop="'+n+'"]');return e?String(e.getAttribute('content')||'').trim():'';}
  var h1=document.querySelector('h1');
  var t=m('og:title')||m('twitter:title')||(h1?h1.textContent:'')||document.title||'';
  var img=m('og:image')||m('og:image:url')||m('twitter:image')||'';
  if(!img){var ims=document.querySelectorAll('article img, main img, img');for(var i=0;i<ims.length&&i<60;i++){var im=ims[i];if((im.naturalWidth||im.width)>=300&&/^https?:/.test(im.currentSrc||im.src||'')){img=im.currentSrc||im.src;break;}}}
  var root=document.querySelector('article')||document.querySelector('[itemprop="articleBody"]')||document.querySelector('main')||document.body;
  var ps=root.querySelectorAll('p');var tx=[];
  for(var j=0;j<ps.length;j++){var s=String(ps[j].innerText||'').replace(/\s+/g,' ').trim();if(s.length>40&&tx.indexOf(s)<0)tx.push(s);}
  var abs='';try{abs=img?new URL(img,location.href).href:'';}catch(e){}
  return JSON.stringify({url:location.href,title:String(t).replace(/\s+/g,' ').trim(),img:abs,desc:m('og:description')||m('description'),text:tx.join('\n').slice(0,8000),site:location.hostname});
})()
