/*
 * ipk-inspect.js -- does a webOS package carry install scripts?
 *
 * App Catalog installs a package itself (com.palm.appinstaller/installNoVerify) only when it
 * has none: the stock installer runs no preinst/postinst/prerm/postrm, which only Preware's
 * root helper ever did. So the catalog reads the package's control.tar.gz for them.
 *
 * An .ipk is an "ar" archive (or, from some packagers, a tar.gz) of debian-binary,
 * control.tar.gz and data.tar.gz. webOS 3's browser has no gzip of its own, so the inflate
 * below is a small port of the well-known "tinf" decoder (RFC 1951), in plain ES5 with plain
 * arrays, because this engine predates typed arrays being dependable.
 *
 *   IpkInspect.scripts(bytes) -> ["postinst", "prerm"] | [] ; throws on a malformed package
 *
 * bytes is an array of numbers 0-255 (see IpkInspect.fromBinaryString).
 */
var IpkInspect = (function () {

    // ---- inflate ----

    function zeros(n) { var a = []; for (var i = 0; i < n; i++) { a.push(0); } return a; }
    function Tree() { this.table = zeros(16); this.trans = zeros(288); }

    var sltree = new Tree(), sdtree = new Tree();
    var lengthBits = zeros(30), lengthBase = zeros(30), distBits = zeros(30), distBase = zeros(30);
    var clcidx = [16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15];
    var codeTree = new Tree();
    var lengths = zeros(288 + 32);
    var offs = zeros(16);

    function buildBitsBase(bits, base, delta, first) {
        var i, sum;
        for (i = 0; i < delta; ++i) { bits[i] = 0; }
        for (i = 0; i < 30 - delta; ++i) { bits[i + delta] = (i / delta) | 0; }
        for (sum = first, i = 0; i < 30; ++i) { base[i] = sum; sum += 1 << bits[i]; }
    }

    function buildFixedTrees(lt, dt) {
        var i;
        for (i = 0; i < 7; ++i) { lt.table[i] = 0; }
        lt.table[7] = 24; lt.table[8] = 152; lt.table[9] = 112;
        for (i = 0; i < 24; ++i) { lt.trans[i] = 256 + i; }
        for (i = 0; i < 144; ++i) { lt.trans[24 + i] = i; }
        for (i = 0; i < 8; ++i) { lt.trans[24 + 144 + i] = 280 + i; }
        for (i = 0; i < 112; ++i) { lt.trans[24 + 144 + 8 + i] = 144 + i; }
        for (i = 0; i < 5; ++i) { dt.table[i] = 0; }
        dt.table[5] = 32;
        for (i = 0; i < 32; ++i) { dt.trans[i] = i; }
    }

    function buildTree(t, lens, off, num) {
        var i, sum;
        for (i = 0; i < 16; ++i) { t.table[i] = 0; }
        for (i = 0; i < num; ++i) { t.table[lens[off + i]]++; }
        t.table[0] = 0;
        for (sum = 0, i = 0; i < 16; ++i) { offs[i] = sum; sum += t.table[i]; }
        for (i = 0; i < num; ++i) { if (lens[off + i]) { t.trans[offs[lens[off + i]]++] = i; } }
    }

    function getBit(d) {
        if (!d.bitcount--) { d.tag = d.src[d.pos++] | 0; d.bitcount = 7; }
        var bit = d.tag & 1;
        d.tag >>>= 1;
        return bit;
    }

    function readBits(d, num, base) {
        if (!num) { return base; }
        while (d.bitcount < 24) { d.tag |= (d.src[d.pos++] | 0) << d.bitcount; d.bitcount += 8; }
        var val = d.tag & (0xffff >>> (16 - num));
        d.tag >>>= num;
        d.bitcount -= num;
        return val + base;
    }

    function decodeSymbol(d, t) {
        while (d.bitcount < 24) { d.tag |= (d.src[d.pos++] | 0) << d.bitcount; d.bitcount += 8; }
        var sum = 0, cur = 0, len = 0, tag = d.tag;
        do {
            cur = 2 * cur + (tag & 1);
            tag >>>= 1;
            ++len;
            if (len > 15) { throw new Error("inflate: bad code"); }
            sum += t.table[len];
            cur -= t.table[len];
        } while (cur >= 0);
        d.tag = tag;
        d.bitcount -= len;
        return t.trans[sum + cur];
    }

    function decodeTrees(d, lt, dt) {
        var hlit = readBits(d, 5, 257), hdist = readBits(d, 5, 1), hclen = readBits(d, 4, 4);
        var i, num, length, sym, prev;
        for (i = 0; i < 19; ++i) { lengths[i] = 0; }
        for (i = 0; i < hclen; ++i) { lengths[clcidx[i]] = readBits(d, 3, 0); }
        buildTree(codeTree, lengths, 0, 19);
        for (num = 0; num < hlit + hdist;) {
            sym = decodeSymbol(d, codeTree);
            if (sym === 16) {
                prev = lengths[num - 1];
                for (length = readBits(d, 2, 3); length; --length) { lengths[num++] = prev; }
            } else if (sym === 17) {
                for (length = readBits(d, 3, 3); length; --length) { lengths[num++] = 0; }
            } else if (sym === 18) {
                for (length = readBits(d, 7, 11); length; --length) { lengths[num++] = 0; }
            } else {
                lengths[num++] = sym;
            }
        }
        buildTree(lt, lengths, 0, hlit);
        buildTree(dt, lengths, hlit, hdist);
    }

    function inflateBlockData(d, lt, dt) {
        for (;;) {
            var sym = decodeSymbol(d, lt);
            if (sym === 256) { return; }
            if (sym < 256) {
                d.out.push(sym);
            } else {
                sym -= 257;
                var length = readBits(d, lengthBits[sym], lengthBase[sym]);
                var dist = decodeSymbol(d, dt);
                var from = d.out.length - readBits(d, distBits[dist], distBase[dist]);
                if (from < 0) { throw new Error("inflate: bad distance"); }
                for (var i = from; i < from + length; ++i) { d.out.push(d.out[i]); }
            }
            if (d.pos > d.src.length + 4) { throw new Error("inflate: ran out of input"); }
        }
    }

    function inflateStored(d) {
        // Give back whole bytes read ahead; the rest of the current byte is padding.
        while (d.bitcount > 8) { d.pos--; d.bitcount -= 8; }
        var length = d.src[d.pos] | (d.src[d.pos + 1] << 8);
        d.pos += 4;
        for (var i = 0; i < length; ++i) { d.out.push(d.src[d.pos++]); }
        d.bitcount = 0;
        d.tag = 0;
    }

    buildBitsBase(lengthBits, lengthBase, 4, 3);
    buildBitsBase(distBits, distBase, 2, 1);
    lengthBits[28] = 0;
    lengthBase[28] = 258;
    buildFixedTrees(sltree, sdtree);

    function inflate(src, start) {
        var d = { src: src, pos: start || 0, tag: 0, bitcount: 0, out: [] };
        var lt = new Tree(), dt = new Tree(), bfinal, btype;
        do {
            bfinal = getBit(d);
            btype = readBits(d, 2, 0);
            if (btype === 0) { inflateStored(d); }
            else if (btype === 1) { inflateBlockData(d, sltree, sdtree); }
            else if (btype === 2) { decodeTrees(d, lt, dt); inflateBlockData(d, lt, dt); }
            else { throw new Error("inflate: bad block type"); }
        } while (!bfinal);
        return d.out;
    }

    function gunzip(b) {
        if (b[0] !== 0x1f || b[1] !== 0x8b || b[2] !== 8) { throw new Error("not gzip"); }
        var flags = b[3], pos = 10;
        if (flags & 4) { pos += 2 + (b[pos] | (b[pos + 1] << 8)); }
        if (flags & 8) { while (b[pos++]) {} }
        if (flags & 16) { while (b[pos++]) {} }
        if (flags & 2) { pos += 2; }
        return inflate(b, pos);
    }

    // ---- archives ----

    function text(b, from, to) {
        var s = "";
        for (var i = from; i < to && b[i]; i++) { s += String.fromCharCode(b[i]); }
        return s;
    }

    // {name: bytes} of an ar archive's members, or of a tar's files when wanted() says so.
    function arMembers(b) {
        var out = {}, pos = 8;
        while (pos + 60 <= b.length) {
            var name = text(b, pos, pos + 16).replace(/\s+$/, "").replace(/\/$/, "");
            var size = parseInt(text(b, pos + 48, pos + 58), 10);
            if (isNaN(size)) { throw new Error("bad ar header"); }
            out[name] = b.slice(pos + 60, pos + 60 + size);
            pos += 60 + size + (size & 1);
        }
        return out;
    }

    function tarEntries(b, wanted) {
        var out = {}, pos = 0;
        while (pos + 512 <= b.length) {
            var name = text(b, pos, pos + 100);
            if (!name) { break; }
            if (text(b, pos + 257, pos + 262) === "ustar") {
                var prefix = text(b, pos + 345, pos + 500);
                if (prefix) { name = prefix + "/" + name; }
            }
            var size = parseInt(text(b, pos + 124, pos + 136).replace(/\s/g, "") || "0", 8);
            var base = name.replace(/^\.\//, "").replace(/\/$/, "");
            if (wanted(base)) { out[base] = b.slice(pos + 512, pos + 512 + size); }
            pos += 512 + Math.ceil(size / 512) * 512;
        }
        return out;
    }

    var SCRIPTS = ["preinst", "postinst", "prerm", "postrm"];

    function scripts(b) {
        var control;
        if (text(b, 0, 8) === "!<arch>\n") {
            control = arMembers(b)["control.tar.gz"];
        } else {
            // The outer tar.gz some packagers wrote.
            control = tarEntries(gunzip(b), function (n) { return n === "control.tar.gz"; })["control.tar.gz"];
        }
        if (!control) { throw new Error("no control.tar.gz"); }
        var files = tarEntries(gunzip(control), function (n) { return SCRIPTS.indexOf(n) >= 0; });
        var found = [];
        for (var i = 0; i < SCRIPTS.length; i++) { if (files[SCRIPTS[i]]) { found.push(SCRIPTS[i]); } }
        return found;
    }

    // A binary XHR's responseText, read with overrideMimeType("text/plain; charset=x-user-defined").
    function fromBinaryString(s) {
        var b = [];
        for (var i = 0; i < s.length; i++) { b.push(s.charCodeAt(i) & 0xff); }
        return b;
    }

    return { scripts: scripts, gunzip: gunzip, fromBinaryString: fromBinaryString };
}());

if (typeof module !== "undefined") { module.exports = IpkInspect; }
