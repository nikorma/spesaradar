/*
 * SpesaRadar - estrattore offerte.
 * Viene iniettato nella WebView a pagina caricata come:
 *   SR_EXTRACT({source:"promoqui", chains:["Lidl", ...]})
 * e restituisce una stringa JSON:
 *   {items:[{title,store,price,oldPrice,unitText,validity,url,sponsored}], follow:url|null, info:{...}}
 */
var SR_EXTRACT = function (cfg) {
  var PRICE_RE = /(?:€\s*(\d{1,4}(?:[.,]\d{1,2})?)(?![\d.,])(?!\s*\/)|(?<![\d.,])(\d{1,4}(?:[.,]\d{1,2})?)\s*€(?!\s*\/))/g;
  var UNIT_RE = /(\d{1,4}(?:[.,]\d{1,3})?)\s*€\s*\/\s*(l|lt|litro|litri|kg|g|100\s*g|100\s*ml|ml|unit[aà]|pz|pezzo|lavaggio|lavaggi|carica|cariche)\b|€\s*(\d{1,4}(?:[.,]\d{1,3})?)\s*\/\s*(l|lt|litro|litri|kg|g|100\s*g|100\s*ml|ml|unit[aà]|pz|pezzo|lavaggio|lavaggi)\b/i;
  var DISCOUNT_RE = /^-?\s*\d{1,2}\s*%$/;
  var VALID_RE = /(fino a|fino al|scade|valid|dal\s+\d|al\s+\d{1,2}\/|\d{1,2}\/\d{1,2}(\/\d{2,4})?\s*-\s*\d{1,2}\/\d{1,2}|oggi|domani|luned|marted|mercoled|gioved|venerd|sabato|domenica)/i;
  var NOISE_RE = /^(vedi offerta|vedi|apri|sfoglia|sfoglia volantino|aggiungi|aggiungi al carrello|salva|acquista|compra|scopri|dettagli|interattivo|offerta|in offerta|nuovo|novit[aà]|sponsorizzato|consegna.*|spedizione.*|gratis|disponibile.*|\d+[.,]?\d*\s*km|ordina.*|filtra.*)$/i;
  var chains = (cfg && cfg.chains) || [];

  function num(s) {
    if (s == null) return null;
    s = String(s).replace(/\s/g, "");
    if (/^\d{1,3}(\.\d{3})+,\d+$/.test(s)) s = s.replace(/\./g, "");
    s = s.replace(",", ".");
    var v = parseFloat(s);
    return isNaN(v) ? null : v;
  }

  function pricesIn(text) {
    var out = [], m;
    PRICE_RE.lastIndex = 0;
    while ((m = PRICE_RE.exec(text)) !== null) {
      var v = num(m[1] || m[2]);
      if (v != null && v > 0 && v < 10000) out.push(v);
    }
    return out;
  }

  function findChain(text) {
    var low = " " + text.toLowerCase() + " ";
    for (var i = 0; i < chains.length; i++) {
      var c = chains[i];
      var keys = c.keys || [c.name || c];
      for (var k = 0; k < keys.length; k++) {
        var key = String(keys[k]).toLowerCase();
        var re = new RegExp("(^|[^a-z0-9àèéìòù])" + key.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "($|[^a-z0-9àèéìòù])");
        if (re.test(low)) return c.name || c;
      }
    }
    return null;
  }

  function abs(href) {
    try { return new URL(href, location.href).href; } catch (e) { return href || null; }
  }

  var SKIP_TAGS = { SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, SVG: 1, TEMPLATE: 1, IFRAME: 1, HEAD: 1 };
  function isChrome(el) {
    var t = el.tagName;
    if (t === "HEADER" || t === "NAV" || t === "FOOTER") return true;
    var r = el.getAttribute && el.getAttribute("role");
    if (r === "navigation" || r === "banner" || r === "contentinfo" || r === "dialog") return true;
    var id = ((el.id || "") + " " + (typeof el.className === "string" ? el.className : "")).toLowerCase();
    return /cookie|consent|iubenda|onetrust|didomi|gdpr|megamenu|breadcrumb/.test(id);
  }

  // Testo di un elemento come lista di "pezzi" (nodi di testo + alt delle immagini).
  function chunksOf(el, out) {
    out = out || [];
    for (var n = el.firstChild; n; n = n.nextSibling) {
      if (n.nodeType === 3) {
        var s = n.nodeValue.replace(/\s+/g, " ").trim();
        if (s) out.push(s);
      } else if (n.nodeType === 1 && !SKIP_TAGS[n.tagName]) {
        if (n.tagName === "IMG") {
          var alt = (n.getAttribute("alt") || "").trim();
          if (alt) out.push("[img] " + alt);
        } else {
          chunksOf(n, out);
        }
      }
    }
    return out;
  }

  function cleanTitleChunk(s) {
    return s.replace(/^\[img\]\s*/, "").trim();
  }

  // Trasforma un "riquadro offerta" in un oggetto offerta.
  var SHIP_RE = /sped|consegna|trasporto|shipping/i;

  function parseCard(el, chunks) {
    var all = chunks.join(" | ");
    var prices = pricesIn(chunks.filter(function (c) {
      return c.indexOf("[img]") !== 0 && !SHIP_RE.test(c);
    }).join(" | "));
    if (!prices.length) return null;
    var price = Math.min.apply(null, prices);
    var maxp = Math.max.apply(null, prices);
    var unitM = all.match(UNIT_RE);
    var unitText = unitM ? unitM[0] : null;
    if (unitM) {
      var u = num(unitM[1] || unitM[3]);
      // se il prezzo unitario e' stato scambiato per prezzo, toglilo
      if (u === price && prices.length > 1) {
        var rest = prices.filter(function (p) { return p !== u; });
        price = Math.min.apply(null, rest);
        maxp = Math.max.apply(null, rest);
      }
    }
    var chain = findChain(all);
    var validity = null, texts = [], imgs = [];
    for (var j = 0; j < chunks.length; j++) {
      var raw = chunks[j];
      var c = cleanTitleChunk(raw);
      if (raw.indexOf("[img]") === 0) { imgs.push(c); continue; }
      if (!validity && VALID_RE.test(c) && c.length < 60 && !pricesIn(c).length) { validity = c; continue; }
      if (pricesIn(c).length || DISCOUNT_RE.test(c) || NOISE_RE.test(c) || UNIT_RE.test(c) || SHIP_RE.test(c)) continue;
      if (!/[a-zà-ù]{2,}/i.test(c)) continue;
      texts.push(c);
    }
    var isChainText = function (t) { return chain && findChain(t) === chain && t.length <= 40; };
    var title = null, used = -1;
    for (var t = 0; t < texts.length; t++) {
      if (isChainText(texts[t])) continue;
      title = texts[t]; used = t;
      if (title.length < 14 && t + 1 < texts.length && !isChainText(texts[t + 1]) &&
          title.toLowerCase().indexOf(texts[t + 1].toLowerCase()) < 0) {
        title = title + " - " + texts[t + 1]; used = t + 1;
      }
      break;
    }
    if (!title) {
      for (var q = 0; q < imgs.length; q++) if (/[a-zà-ù]{3,}/i.test(imgs[q])) { title = imgs[q]; break; }
    }
    if (!title) return null;
    var store = chain;
    if (!store) {
      for (var s = used + 1; s < texts.length; s++) {
        if (texts[s].length <= 30) { store = texts[s]; break; }
      }
    }
    if (!store) {
      for (var i = 0; i < imgs.length; i++) {
        var a = imgs[i];
        if (a.length >= 2 && a.length <= 40 && a !== title && !/logo|immagine|image|foto|prodotto/i.test(a)) { store = a; break; }
      }
    }
    var a2 = el.tagName === "A" ? el : (el.querySelector("a[href]") || (el.closest && el.closest("a[href]")));
    return {
      title: title.slice(0, 160),
      store: store,
      price: price,
      oldPrice: maxp > price ? maxp : null,
      unitText: unitText,
      validity: validity,
      url: a2 ? abs(a2.getAttribute("href")) : null,
      sponsored: /sponsorizzat/i.test(all)
    };
  }

  // Cerca i riquadri offerta: il contenitore piu' grande che contiene una sola offerta.
  function genericCards(root) {
    var stats = new Map();
    function walk(el) {
      if (SKIP_TAGS[el.tagName] || isChrome(el)) { stats.set(el, { p: 999, c: 99999 }); return stats.get(el); }
      var p = 0, c = 0;
      for (var n = el.firstChild; n; n = n.nextSibling) {
        if (n.nodeType === 3) {
          var s = n.nodeValue.replace(/\s+/g, " ").trim();
          if (s) { c += s.length; p += pricesIn(s).length; }
        } else if (n.nodeType === 1) {
          if (n.tagName === "IMG") { c += (n.getAttribute("alt") || "").length; continue; }
          var r = walk(n);
          p += r.p; c += r.c;
        }
      }
      var st = { p: p, c: c };
      stats.set(el, st);
      return st;
    }
    walk(root);
    var cards = [];
    stats.forEach(function (st, el) {
      if (st.p < 1 || st.p > 4 || st.c < 8 || st.c > 450) return;
      var par = el.parentElement;
      var ps = par && stats.get(par);
      if (ps && ps.p <= 4 && ps.c <= 450 && ps.p === st.p) return; // il genitore e' ancora la stessa offerta
      cards.push(el);
    });
    var items = [], seen = {};
    cards.forEach(function (el) {
      var it = parseCard(el, chunksOf(el));
      if (!it) return;
      var k = it.title + "|" + it.price + "|" + (it.store || "");
      if (seen[k]) return;
      seen[k] = 1;
      items.push(it);
    });
    return items;
  }

  function jsonLd() {
    var out = [];
    var nodes = document.querySelectorAll('script[type="application/ld+json"]');
    function visit(o) {
      if (!o || typeof o !== "object") return;
      if (Array.isArray(o)) { o.forEach(visit); return; }
      if (o["@graph"]) visit(o["@graph"]);
      if (o.itemListElement) visit(o.itemListElement);
      if (o.item) visit(o.item);
      var t = [].concat(o["@type"] || []).join(",");
      if (/Product/.test(t) && o.offers) {
        var offs = [].concat(o.offers);
        offs.forEach(function (of) {
          var pr = num(of.price != null ? of.price : of.lowPrice);
          if (pr == null) return;
          var seller = of.seller && (of.seller.name || of.seller);
          var brand = o.brand && (o.brand.name || o.brand);
          var title = o.name || "";
          if (brand && title.toLowerCase().indexOf(String(brand).toLowerCase()) < 0) title = brand + " - " + title;
          out.push({
            title: String(title).slice(0, 160),
            store: seller ? (findChain(String(seller)) || String(seller)) : null,
            price: pr, oldPrice: null, unitText: null,
            validity: of.priceValidUntil ? ("fino al " + of.priceValidUntil) : null,
            url: of.url || o.url || null, sponsored: false
          });
        });
      }
    }
    for (var i = 0; i < nodes.length; i++) {
      try { visit(JSON.parse(nodes[i].textContent)); } catch (e) {}
    }
    return out;
  }

  function amazon() {
    var cards = document.querySelectorAll('[data-component-type="s-search-result"], div[data-asin][data-index]');
    var items = [], seen = {};
    for (var i = 0; i < cards.length; i++) {
      var c = cards[i];
      var asin = c.getAttribute("data-asin");
      if (!asin || seen[asin]) continue;
      var h2 = c.querySelector("h2");
      var title = h2 ? ((h2.getAttribute("aria-label") || "") || h2.textContent).replace(/\s+/g, " ").trim() : null;
      if (!title) continue;
      var pEl = c.querySelector(".a-price:not(.a-text-price) .a-offscreen") || c.querySelector(".a-price .a-offscreen");
      var price = pEl ? pricesIn(pEl.textContent)[0] : null;
      if (price == null) {
        var w = c.querySelector(".a-price-whole"), f = c.querySelector(".a-price-fraction");
        if (w) price = num(w.textContent.replace(/[^\d]/g, "") + "." + (f ? f.textContent.replace(/[^\d]/g, "") : "00"));
      }
      if (price == null) continue;
      var oEl = c.querySelector(".a-price.a-text-price .a-offscreen");
      var old = oEl ? pricesIn(oEl.textContent)[0] : null;
      var text = chunksOf(c).join(" ");
      var um = text.match(UNIT_RE);
      seen[asin] = 1;
      items.push({
        title: title.slice(0, 160),
        store: "Amazon",
        price: price,
        oldPrice: old && old > price ? old : null,
        unitText: um ? um[0] : null,
        validity: null,
        url: "https://www.amazon.it/dp/" + asin,
        sponsored: /sponsorizzat/i.test(text) || c.getAttribute("data-component-type") === "sp-sponsored-result"
      });
    }
    return items;
  }

  // PromoQui / ShopFully: schede con data-testid stabili.
  function promoqui() {
    var cards = document.querySelectorAll('a[data-testid="flyer_gib"]');
    var items = [], seen = {};
    for (var i = 0; i < cards.length; i++) {
      var a = cards[i];
      var h3 = a.querySelector("h3");
      var title = h3 ? h3.textContent.replace(/\s+/g, " ").trim() : "";
      if (!title) { var im = a.querySelector("img[alt]"); title = im ? im.getAttribute("alt").trim() : ""; }
      var pEl = a.querySelector('[data-testid="flyer_gib_price"]');
      var price = pEl ? pricesIn(pEl.textContent)[0] : null;
      if (!title || price == null) continue;
      var rEl = a.querySelector('[data-testid="flyer_item_retailer_name"]');
      var retailer = rEl ? rEl.textContent.replace(/\s+/g, " ").trim() : null;
      var href = a.getAttribute("href") || "";
      var slugM = href.match(/\/volantino\/([^\/?#]+)/);
      var slug = slugM ? slugM[1].replace(/-/g, " ") : "";
      var store = findChain(retailer || "") || findChain(slug) || retailer;
      var dist = null;
      if (rEl && rEl.parentElement) {
        var dm = rEl.parentElement.textContent.match(/(\d+(?:[.,]\d+)?)\s*km/i);
        if (dm) dist = num(dm[1]);
      }
      var olds = [];
      a.querySelectorAll("s, del, .line-through").forEach(function (o) { olds = olds.concat(pricesIn(o.textContent)); });
      var old = olds.length ? Math.max.apply(null, olds) : null;
      var txt = chunksOf(a).join(" | ");
      var um = txt.match(UNIT_RE);
      var vm = null, ch = chunksOf(a);
      for (var k = 0; k < ch.length; k++) if (VALID_RE.test(ch[k]) && ch[k].length < 60 && !/km$/i.test(ch[k])) { vm = ch[k]; break; }
      var key = title + "|" + (retailer || "") + "|" + price;
      if (seen[key]) continue;
      seen[key] = 1;
      items.push({
        title: title.slice(0, 160),
        store: store,
        price: price,
        oldPrice: old && old > price ? old : null,
        unitText: um ? um[0] : null,
        validity: vm,
        url: abs(href),
        sponsored: false,
        distanceKm: dist
      });
    }
    return items;
  }

  function trovaprezziFollow() {
    if (/\/prezzo_|\/offerte\//.test(location.pathname)) return null;
    var words = String((cfg && cfg.query) || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "")
      .split(/[^a-z0-9]+/).filter(function (w) { return w.length >= 3; });
    var links = document.querySelectorAll('a[href*="/prezzo_"]');
    var best = null, bestHits = 0;
    for (var i = 0; i < links.length; i++) {
      var h = (links[i].getAttribute("href") || "").toLowerCase();
      if (!/\/prezzo_[a-z-]+_.+\.aspx/i.test(h)) continue;
      if (links[i].closest("nav, header, footer, [class*=menu], [class*=Menu]")) continue;
      var hits = words.filter(function (w) { return h.indexOf(w.slice(0, 5)) >= 0; }).length;
      if (hits > bestHits) { best = abs(links[i].getAttribute("href")); bestHits = hits; }
    }
    return bestHits >= Math.max(1, Math.ceil(words.length / 2)) ? best : null;
  }

  var res = { items: [], follow: null, info: { url: location.href, title: document.title, method: "" } };
  try {
    var src = cfg && cfg.source;
    if (src === "promoqui") {
      res.items = promoqui();
      res.info.method = "promoqui";
      if (!res.items.length) { res.items = genericCards(document.body); res.info.method = "promoqui-generico"; }
    } else if (src === "amazon") {
      res.items = amazon();
      res.info.method = "amazon";
      if (!res.items.length) { res.items = genericCards(document.body); res.info.method = "amazon-generico"; }
      if (/captcha|robot/i.test(document.title + " " + (document.body ? document.body.textContent.slice(0, 2000) : ""))) res.info.blocked = true;
    } else {
      if (src === "trovaprezzi") res.follow = trovaprezziFollow();
      if (!res.follow) {
        var ld = jsonLd();
        var gen = genericCards(document.body);
        res.items = gen.length >= ld.length ? gen : ld;
        res.info.method = gen.length >= ld.length ? "generico" : "json-ld";
        res.info.ldCount = ld.length;
        res.info.genCount = gen.length;
      }
    }
  } catch (e) {
    res.info.error = String(e && e.message || e);
  }
  return JSON.stringify(res);
};
