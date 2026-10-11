#include "xenia/core/Lang.hpp"
#include "xenia/bot/Macro.hpp"
#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iterator>
#include <ctime>
using namespace geode::prelude;
namespace fs = std::filesystem;

namespace xenia::bot {
    static Macro g_cur;
    static bool g_has = false;

    fs::path MacroManager::dir() {
#ifdef GEODE_IS_ANDROID
        fs::path d = "/storage/emulated/0/Android/media/com.geode.launcher/game/nxr_tas";
        std::error_code ec;
        fs::create_directories(d, ec);
        if (!ec && fs::is_directory(d, ec)) return d;
#endif
        auto fb = Mod::get()->getSaveDir() / "macros";
        std::error_code ec2;
        fs::create_directories(fb, ec2);
        return fb;
    }

    static fs::path pathOf(std::string const& name) {
        return MacroManager::dir() / (name + ".nxr");
    }

    bool MacroManager::validName(std::string const& n) {
        if (n.empty() || n.size() > 32) return false;
        for (unsigned char c : n) {
            if (!(std::isalnum(c) || c == '_' || c == '-')) return false;
        }
        return true;
    }

    bool MacroManager::exists(std::string const& name) {
        if (!validName(name)) return false;
        std::error_code ec;
        return fs::is_regular_file(pathOf(name), ec);
    }

    std::vector<std::string> MacroManager::list() {
        std::vector<std::string> out;
        std::error_code ec;
        for (fs::directory_iterator it(dir(), ec), end; !ec && it != end; it.increment(ec)) {
            std::error_code ec2;
            if (!it->is_regular_file(ec2) || it->path().extension() != ".nxr") continue;
            auto stem = it->path().stem().string();
            if (validName(stem)) out.push_back(stem);
        }
        std::sort(out.begin(), out.end());
        return out;
    }

    static void put32(std::vector<unsigned char>& b, uint32_t v) {
        for (int i = 0; i < 4; i++) b.push_back((unsigned char)((v >> (8 * i)) & 0xFF));
    }

    static uint32_t get32(unsigned char const* p) {
        return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
    }

    static void putf32(std::vector<unsigned char>& b, float f) {
        uint32_t v;
        std::memcpy(&v, &f, 4);
        put32(b, v);
    }

    static float getf32(unsigned char const* p) {
        uint32_t v = get32(p);
        float f;
        std::memcpy(&f, &v, 4);
        return f;
    }

    static void put64d(std::vector<unsigned char>& b, double d) {
        uint64_t v;
        std::memcpy(&v, &d, 8);
        for (int i = 0; i < 8; i++) b.push_back((unsigned char)((v >> (8 * i)) & 0xFF));
    }

    static double get64d(unsigned char const* p) {
        uint64_t v = 0;
        for (int i = 0; i < 8; i++) v |= (uint64_t)p[i] << (8 * i);
        double d;
        std::memcpy(&d, &v, 8);
        return d;
    }

    static void put64i(std::vector<unsigned char>& b, long long v) {
        uint64_t u = (uint64_t)v;
        for (int i = 0; i < 8; i++) b.push_back((unsigned char)((u >> (8 * i)) & 0xFF));
    }

    static long long get64i(unsigned char const* p) {
        uint64_t v = 0;
        for (int i = 0; i < 8; i++) v |= (uint64_t)p[i] << (8 * i);
        return (long long)v;
    }

    static std::string writeFile(fs::path const& p, Macro const& m) {
        std::vector<unsigned char> b;
        b.reserve(72 + m.frames.size() * 8 + m.samples.size() * 40);
        b.insert(b.end(), {'N', 'X', 'R', '3'});
        put32(b, (uint32_t)m.frames.size());
        put64d(b, m.tps);
        put32(b, (uint32_t)m.levelId);
        put64i(b, m.created);
        put64i(b, m.modified);
        std::string nm = m.levelName.substr(0, 64);
        b.push_back((unsigned char)(nm.size() & 0xFF));
        b.push_back((unsigned char)((nm.size() >> 8) & 0xFF));
        b.insert(b.end(), nm.begin(), nm.end());
        put32(b, (uint32_t)m.samples.size());
        for (auto const& f : m.frames) {
            put32(b, (uint32_t)f.frame);
            b.push_back(f.down ? 1 : 0);
            b.push_back(f.player2 ? 1 : 0);
            b.push_back((unsigned char)f.button);
            b.push_back(f.half ? 1 : 0);
        }
        for (auto const& sp : m.samples) {
            put32(b, (uint32_t)sp.frame);
            b.push_back(sp.hasP2 ? 1 : 0);
            b.push_back(0);
            b.push_back(0);
            b.push_back(0);
            putf32(b, sp.x1);
            putf32(b, sp.y1);
            put64d(b, sp.v1);
            putf32(b, sp.x2);
            putf32(b, sp.y2);
            put64d(b, sp.v2);
        }
        auto tmp = p;
        tmp += ".tmp";
        {
            std::ofstream out(tmp, std::ios::binary | std::ios::trunc);
            if (!out) return xenia::tr("Macro file cannot be written", "File makro tidak bisa ditulis");
            out.write(reinterpret_cast<char const*>(b.data()), (std::streamsize)b.size());
            out.flush();
            if (!out) return xenia::tr("Failed to write macro file", "Gagal menulis file makro");
        }
        std::error_code ec;
        fs::rename(tmp, p, ec);
        if (ec) {
            fs::remove(p, ec);
            std::error_code ec2;
            fs::rename(tmp, p, ec2);
            if (ec2) return xenia::tr("Failed to save macro file", "Gagal menyimpan file makro");
        }
        return "";
    }

    static std::string readFile(fs::path const& p, Macro& m) {
        std::ifstream in(p, std::ios::binary);
        if (!in) return xenia::tr("Macro file not found", "File makro tidak ditemukan");
        std::vector<unsigned char> b((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
        if (b.size() < 16) return xenia::tr("File is not a valid NXR macro", "File bukan format NXR yang valid");
        bool v3 = std::memcmp(b.data(), "NXR3", 4) == 0;
        bool v2 = v3 || std::memcmp(b.data(), "NXR2", 4) == 0;
        bool v1 = std::memcmp(b.data(), "NXR1", 4) == 0;
        if (!v1 && !v2) return xenia::tr("File is not a valid NXR macro", "File bukan format NXR yang valid");
        uint32_t count = get32(b.data() + 4);
        if (count > MacroManager::kMaxFrames) return xenia::tr("Macro file is too large", "File makro terlalu besar");
        m.tps = get64d(b.data() + 8);
        if (!std::isfinite(m.tps) || m.tps < 1.0 || m.tps > 1e12) return xenia::tr("Macro file is corrupted (invalid TPS)", "File makro rusak (TPS tidak valid)");
        size_t off = 16;
        m.levelId = 0;
        m.levelName.clear();
        m.created = m.modified = 0;
        if (v2) {
            if (b.size() < 16 + 4 + 8 + 8 + 2) return xenia::tr("Macro file is corrupted (header)", "File makro rusak (header)");
            m.levelId = (int)get32(b.data() + off); off += 4;
            m.created = get64i(b.data() + off); off += 8;
            m.modified = get64i(b.data() + off); off += 8;
            size_t nl = (size_t)b[off] | ((size_t)b[off + 1] << 8); off += 2;
            if (b.size() < off + nl) return xenia::tr("Macro file is corrupted (header)", "File makro rusak (header)");
            m.levelName.assign(reinterpret_cast<char const*>(b.data() + off), nl);
            off += nl;
        }
        uint32_t sampleCount = 0;
        if (v3) {
            if (b.size() < off + 4) return xenia::tr("Macro file is corrupted (header)", "File makro rusak (header)");
            sampleCount = get32(b.data() + off);
            off += 4;
            if (sampleCount > MacroManager::kMaxSamples) return xenia::tr("Macro file is too large", "File makro terlalu besar");
        }
        if (b.size() != off + (size_t)count * 8 + (size_t)sampleCount * 40) return xenia::tr("Macro file is corrupted (size mismatch)", "File makro rusak (ukuran tidak cocok)");
        m.frames.clear();
        m.samples.clear();
        m.frames.reserve(count);
        for (uint32_t i = 0; i < count; i++) {
            auto const* r = b.data() + off + (size_t)i * 8;
            int fr = (int)get32(r);
            if (fr < 0) continue;
            int btn = (int)r[6];
            if (btn < 1 || btn > 3) continue;
            m.frames.push_back({fr, r[4] != 0, r[5] != 0, btn, r[7] != 0});
        }
        std::stable_sort(m.frames.begin(), m.frames.end(), [](Frame const& x, Frame const& y) { return x.frame < y.frame; });
        m.samples.reserve(sampleCount);
        size_t so = off + (size_t)count * 8;
        for (uint32_t i = 0; i < sampleCount; i++) {
            auto const* r = b.data() + so + (size_t)i * 40;
            Sample sp;
            sp.frame = (int)get32(r);
            sp.hasP2 = r[4] != 0;
            sp.x1 = getf32(r + 8);
            sp.y1 = getf32(r + 12);
            sp.v1 = get64d(r + 16);
            sp.x2 = getf32(r + 24);
            sp.y2 = getf32(r + 28);
            sp.v2 = get64d(r + 32);
            if (sp.frame < 0) continue;
            if (!std::isfinite(sp.x1) || !std::isfinite(sp.y1) || !std::isfinite(sp.v1)) continue;
            if (!std::isfinite(sp.x2) || !std::isfinite(sp.y2) || !std::isfinite(sp.v2)) sp.hasP2 = false;
            m.samples.push_back(sp);
        }
        std::stable_sort(m.samples.begin(), m.samples.end(), [](Sample const& x, Sample const& y) { return x.frame < y.frame; });
        return "";
    }

    std::string MacroManager::create(std::string const& name) {
        if (!validName(name)) return xenia::tr("Name: 1-32 characters (letters, digits, _ or -)", "Nama: 1-32 karakter (huruf, angka, _ atau -)");
        if (exists(name)) return xenia::tr("A macro with that name already exists", "Makro dengan nama itu sudah ada");
        Macro nm;
        nm.name = name;
        nm.created = nm.modified = (long long)std::time(nullptr);
        if (auto e = writeFile(pathOf(name), nm); !e.empty()) return e;
        g_cur = nm;
        g_has = true;
        return "";
    }

    std::string MacroManager::remove(std::string const& name) {
        if (!exists(name)) return xenia::tr("Macro not found", "Makro tidak ditemukan");
        std::error_code ec;
        fs::remove(pathOf(name), ec);
        if (ec) return xenia::tr("Failed to delete macro file", "Gagal menghapus file makro");
        if (g_has && g_cur.name == name) {
            g_cur = Macro{};
            g_has = false;
        }
        return "";
    }

    std::string MacroManager::rename(std::string const& from, std::string const& to) {
        if (!exists(from)) return xenia::tr("Macro not found", "Makro tidak ditemukan");
        if (!validName(to)) return xenia::tr("Name: 1-32 characters (letters, digits, _ or -)", "Nama: 1-32 karakter (huruf, angka, _ atau -)");
        if (from == to) return "";
        if (exists(to)) return xenia::tr("A macro with that name already exists", "Makro dengan nama itu sudah ada");
        std::error_code ec;
        fs::rename(pathOf(from), pathOf(to), ec);
        if (ec) return xenia::tr("Failed to rename macro", "Gagal mengganti nama makro");
        if (g_has && g_cur.name == from) g_cur.name = to;
        return "";
    }

    std::string MacroManager::load(std::string const& name) {
        if (!exists(name)) return xenia::tr("Macro not found", "Makro tidak ditemukan");
        Macro m;
        m.name = name;
        if (auto e = readFile(pathOf(name), m); !e.empty()) return e;
        g_cur = std::move(m);
        g_has = true;
        return "";
    }

    std::string MacroManager::save() {
        if (!g_has) return xenia::tr("No macro is loaded", "Tidak ada makro yang dimuat");
        g_cur.modified = (long long)std::time(nullptr);
        if (g_cur.created == 0) g_cur.created = g_cur.modified;
        return writeFile(pathOf(g_cur.name), g_cur);
    }

    bool MacroManager::hasCurrent() { return g_has; }
    Macro& MacroManager::current() { return g_cur; }

    static std::string fmtTime(long long t) {
        if (t <= 0) return "-";
        std::time_t tt = (std::time_t)t;
        std::tm tmv{};
#ifdef _WIN32
        localtime_s(&tmv, &tt);
#else
        localtime_r(&tt, &tmv);
#endif
        char buf[32];
        if (!std::strftime(buf, sizeof buf, "%Y-%m-%d %H:%M", &tmv)) return "-";
        return buf;
    }

    std::string MacroManager::info() {
        if (!g_has) return xenia::tr("No macro loaded.", "Belum ada makro dimuat.");
        auto& m = g_cur;
        int last = m.frames.empty() ? 0 : m.frames.back().frame;
        int downs = 0, p2 = 0;
        for (auto const& f : m.frames) {
            if (f.down) downs++;
            if (f.player2) p2++;
        }
        std::error_code ec;
        auto path = pathOf(m.name);
        auto sz = fs::is_regular_file(path, ec) ? fs::file_size(path, ec) : 0;
        std::string lvl = m.levelId != 0 ? fmt::format("{} (ID {})", m.levelName.empty() ? std::string("-") : m.levelName, m.levelId)
                                         : std::string(xenia::tr("none (record in a level first)", "belum ada (rekam dulu di level)"));
        return xenia::trf(
            "Name: {}\nLevel: {}\nInputs: {} ({} presses, {} releases, {} on P2)\nTPS: {}\nPosition corrections: {} samples\nLast frame: {} (~{:.2f} s)\nCreated: {}\nModified: {}\nFile: {}.nxr ({} bytes)\nFolder: {}", "Nama: {}\nLevel: {}\nInput: {} ({} klik, {} lepas, {} input P2)\nTPS: {}\nKoreksi posisi: {} sampel\nFrame akhir: {} (~{:.2f} dtk)\nDibuat: {}\nDiubah: {}\nFile: {}.nxr ({} byte)\nFolder: {}",
            m.name, lvl, m.frames.size(), downs, (int)m.frames.size() - downs, p2, m.tps, m.samples.size(), last,
            m.tps > 0 ? last / m.tps : 0.0, fmtTime(m.created), fmtTime(m.modified), m.name, (unsigned long long)sz,
            MacroManager::dir().string());
    }
}
