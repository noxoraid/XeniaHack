#include "xenia/bot/Macro.hpp"
#include "xenia/core/Lang.hpp"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cmath>
#include <cstring>
#include <fstream>
#include <span>

using namespace geode::prelude;
namespace fs = std::filesystem;

namespace xenia::bot {
    namespace {
        Macro s_currentMacro;
        bool s_hasMacro = false;

        fs::path macroPath(std::string_view name) {
            return MacroManager::dir() / fmt::format("{}.nxr", name);
        }

        template <typename T>
        void writePOD(std::vector<uint8_t>& buf, T const& val) {
            static_assert(std::is_trivially_copyable_v<T>);
            size_t const offset = buf.size();
            buf.resize(offset + sizeof(T));
            std::memcpy(buf.data() + offset, &val, sizeof(T));
        }

        template <typename T>
        bool readPOD(std::span<uint8_t const> buf, size_t& offset, T& out) {
            static_assert(std::is_trivially_copyable_v<T>);
            if (offset + sizeof(T) > buf.size()) {
                return false;
            }
            std::memcpy(&out, buf.data() + offset, sizeof(T));
            offset += sizeof(T);
            return true;
        }

        Result<void> serializeToFile(fs::path const& path, Macro const& macro) {
            std::vector<uint8_t> buffer;
            buffer.reserve(72 + macro.frames.size() * 8 + macro.samples.size() * 40);

            buffer.insert(buffer.end(), {'N', 'X', 'R', '3'});

            uint32_t const frameCount = static_cast<uint32_t>(macro.frames.size());
            writePOD(buffer, frameCount);
            writePOD(buffer, macro.tps);
            writePOD(buffer, static_cast<uint32_t>(macro.levelId));
            writePOD(buffer, macro.created);
            writePOD(buffer, macro.modified);

            std::string const levelName = macro.levelName.substr(0, 64);
            uint16_t const nameLen = static_cast<uint16_t>(levelName.size());
            writePOD(buffer, nameLen);
            buffer.insert(buffer.end(), levelName.begin(), levelName.end());

            uint32_t const sampleCount = static_cast<uint32_t>(macro.samples.size());
            writePOD(buffer, sampleCount);

            for (auto const& f : macro.frames) {
                writePOD(buffer, static_cast<uint32_t>(f.frame));
                buffer.push_back(f.down ? 1 : 0);
                buffer.push_back(f.player2 ? 1 : 0);
                buffer.push_back(static_cast<uint8_t>(f.button));
                buffer.push_back(f.half ? 1 : 0);
            }

            for (auto const& s : macro.samples) {
                writePOD(buffer, static_cast<uint32_t>(s.frame));
                buffer.push_back(s.hasP2 ? 1 : 0);
                buffer.insert(buffer.end(), {0, 0, 0});
                writePOD(buffer, s.x1);
                writePOD(buffer, s.y1);
                writePOD(buffer, s.v1);
                writePOD(buffer, s.x2);
                writePOD(buffer, s.y2);
                writePOD(buffer, s.v2);
            }

            std::error_code ec;
            fs::path const tempPath = path.string() + ".tmp";
            {
                std::ofstream file(tempPath, std::ios::binary | std::ios::trunc);
                if (!file.is_open()) {
                    return Err(xenia::tr("Failed to open macro file for writing", "Gagal membuka file makro untuk ditulis"));
                }
                file.write(reinterpret_cast<char const*>(buffer.data()), static_cast<std::streamsize>(buffer.size()));
                if (!file.good()) {
                    return Err(xenia::tr("I/O error while writing macro", "Terjadi kesalahan saat menulis makro"));
                }
            }

            fs::rename(tempPath, path, ec);
            if (ec) {
                fs::remove(path, ec);
                fs::rename(tempPath, path, ec);
                if (ec) {
                    return Err(xenia::tr("Failed to finalize macro file save", "Gagal menyelesaikan penyimpanan makro"));
                }
            }

            return Ok();
        }

        Result<Macro> deserializeFromFile(fs::path const& path) {
            std::ifstream file(path, std::ios::binary | std::ios::ate);
            if (!file.is_open()) {
                return Err(xenia::tr("Macro file not found", "File makro tidak ditemukan"));
            }

            auto const fileSize = file.tellg();
            if (fileSize < 16) {
                return Err(xenia::tr("Invalid NXR header size", "Ukuran header NXR tidak valid"));
            }

            file.seekg(0, std::ios::beg);
            std::vector<uint8_t> buffer(static_cast<size_t>(fileSize));
            file.read(reinterpret_cast<char*>(buffer.data()), fileSize);

            if (!file.good()) {
                return Err(xenia::tr("Failed to read macro payload", "Gagal membaca isi file makro"));
            }

            std::span<uint8_t const> const data(buffer);
            bool const v3 = std::memcmp(data.data(), "NXR3", 4) == 0;
            bool const v2 = v3 || std::memcmp(data.data(), "NXR2", 4) == 0;
            bool const v1 = std::memcmp(data.data(), "NXR1", 4) == 0;

            if (!v1 && !v2) {
                return Err(xenia::tr("Unsupported macro signature", "Format tanda tangan makro tidak didukung"));
            }

            size_t offset = 4;
            uint32_t frameCount = 0;
            if (!readPOD(data, offset, frameCount) || frameCount > MacroManager::kMaxFrames) {
                return Err(xenia::tr("Corrupted or oversized frame table", "Tabel frame rusak atau terlalu besar"));
            }

            Macro macro;
            if (!readPOD(data, offset, macro.tps) || !std::isfinite(macro.tps) || macro.tps < 1.0) {
                return Err(xenia::tr("Corrupted TPS rate in header", "Nilai TPS di header tidak valid"));
            }

            if (v2) {
                uint32_t levelId = 0;
                uint16_t nameLen = 0;
                if (!readPOD(data, offset, levelId) ||
                    !readPOD(data, offset, macro.created) ||
                    !readPOD(data, offset, macro.modified) ||
                    !readPOD(data, offset, nameLen)) {
                    return Err(xenia::tr("Corrupted v2/v3 metadata block", "Blok metadata v2/v3 rusak"));
                }
                macro.levelId = static_cast<int>(levelId);

                if (offset + nameLen > data.size()) {
                    return Err(xenia::tr("Corrupted level name segment", "Segmen nama level rusak"));
                }
                macro.levelName.assign(reinterpret_cast<char const*>(data.data() + offset), nameLen);
                offset += nameLen;
            }

            uint32_t sampleCount = 0;
            if (v3) {
                if (!readPOD(data, offset, sampleCount) || sampleCount > MacroManager::kMaxSamples) {
                    return Err(xenia::tr("Corrupted sample segment count", "Jumlah sampel rusak"));
                }
            }

            size_t const expectedPayload = offset + (static_cast<size_t>(frameCount) * 8) + (static_cast<size_t>(sampleCount) * 40);
            if (data.size() != expectedPayload) {
                return Err(xenia::tr("File length mismatch with payload header", "Panjang file tidak sesuai dengan deklarasi header"));
            }

            macro.frames.reserve(frameCount);
            for (uint32_t i = 0; i < frameCount; ++i) {
                uint32_t frameNum = 0;
                readPOD(data, offset, frameNum);
                uint8_t const down = data[offset++];
                uint8_t const p2 = data[offset++];
                uint8_t const btn = data[offset++];
                uint8_t const half = data[offset++];

                if (btn >= 1 && btn <= 3) {
                    macro.frames.push_back({
                        static_cast<int>(frameNum),
                        down != 0,
                        p2 != 0,
                        btn,
                        half != 0
                    });
                }
            }

            std::ranges::stable_sort(macro.frames, [](Frame const& a, Frame const& b) {
                return a.frame < b.frame;
            });

            macro.samples.reserve(sampleCount);
            for (uint32_t i = 0; i < sampleCount; ++i) {
                Sample sample;
                uint32_t frameNum = 0;
                readPOD(data, offset, frameNum);
                sample.frame = static_cast<int>(frameNum);
                sample.hasP2 = data[offset] != 0;
                offset += 4; // flag + 3 pad bytes

                readPOD(data, offset, sample.x1);
                readPOD(data, offset, sample.y1);
                readPOD(data, offset, sample.v1);
                readPOD(data, offset, sample.x2);
                readPOD(data, offset, sample.y2);
                readPOD(data, offset, sample.v2);

                if (std::isfinite(sample.x1) && std::isfinite(sample.y1) && std::isfinite(sample.v1)) {
                    if (!std::isfinite(sample.x2) || !std::isfinite(sample.y2) || !std::isfinite(sample.v2)) {
                        sample.hasP2 = false;
                    }
                    macro.samples.push_back(sample);
                }
            }

            std::ranges::stable_sort(macro.samples, [](Sample const& a, Sample const& b) {
                return a.frame < b.frame;
            });

            return Ok(macro);
        }
    }

    fs::path MacroManager::dir() {
        std::error_code ec;
#ifdef GEODE_IS_ANDROID
        fs::path const androidDir = "/storage/emulated/0/Android/media/com.geode.launcher/game/nxr_tas";
        fs::create_directories(androidDir, ec);
        if (!ec && fs::is_directory(androidDir, ec)) {
            return androidDir;
        }
#endif
        auto const fallback = Mod::get()->getSaveDir() / "macros";
        fs::create_directories(fallback, ec);
        return fallback;
    }

    bool MacroManager::validName(std::string_view name) {
        if (name.empty() || name.size() > 32) return false;
        return std::ranges::all_of(name, [](char c) {
            return std::isalnum(static_cast<unsigned char>(c)) || c == '_' || c == '-';
        });
    }

    bool MacroManager::exists(std::string_view name) {
        if (!validName(name)) return false;
        std::error_code ec;
        return fs::is_regular_file(macroPath(name), ec);
    }

    std::vector<std::string> MacroManager::list() {
        std::vector<std::string> results;
        std::error_code ec;
        for (auto const& entry : fs::directory_iterator(dir(), ec)) {
            if (entry.is_regular_file() && entry.path().extension() == ".nxr") {
                auto stem = entry.path().stem().string();
                if (validName(stem)) {
                    results.push_back(std::move(stem));
                }
            }
        }
        std::ranges::sort(results);
        return results;
    }

    Result<void> MacroManager::create(std::string_view name) {
        if (!validName(name)) {
            return Err(xenia::tr("Invalid name (1-32 chars, alphanumeric, _ or -)", "Nama tidak valid (1-32 karakter, alfanumerik, _ atau -)"));
        }
        if (exists(name)) {
            return Err(xenia::tr("A macro with that name already exists", "Makro dengan nama tersebut sudah ada"));
        }

        Macro macro;
        macro.name = std::string(name);
        auto const now = std::chrono::duration_cast<std::chrono::seconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
        macro.created = now;
        macro.modified = now;

        GEODE_UNWRAP(serializeToFile(macroPath(name), macro));

        s_currentMacro = std::move(macro);
        s_hasMacro = true;
        return Ok();
    }

    Result<void> MacroManager::remove(std::string_view name) {
        if (!exists(name)) {
            return Err(xenia::tr("Macro file not found", "File makro tidak ditemukan"));
        }
        std::error_code ec;
        fs::remove(macroPath(name), ec);
        if (ec) {
            return Err(xenia::tr("Failed to delete file from disk", "Gagal menghapus file dari disk"));
        }
        if (s_hasMacro && s_currentMacro.name == name) {
            s_currentMacro = Macro{};
            s_hasMacro = false;
        }
        return Ok();
    }

    Result<void> MacroManager::rename(std::string_view from, std::string_view to) {
        if (!exists(from)) {
            return Err(xenia::tr("Source macro not found", "Makro asal tidak ditemukan"));
        }
        if (!validName(to)) {
            return Err(xenia::tr("Target name is invalid", "Nama tujuan tidak valid"));
        }
        if (exists(to)) {
            return Err(xenia::tr("Target name already exists", "Nama tujuan sudah digunakan"));
        }

        std::error_code ec;
        fs::rename(macroPath(from), macroPath(to), ec);
        if (ec) {
            return Err(xenia::tr("Failed to rename macro file", "Gagal mengubah nama file makro"));
        }

        if (s_hasMacro && s_currentMacro.name == from) {
            s_currentMacro.name = std::string(to);
        }
        return Ok();
    }

    Result<void> MacroManager::load(std::string_view name) {
        auto parsed = deserializeFromFile(macroPath(name));
        if (parsed.isErr()) {
            return Err(parsed.unwrapErr());
        }
        s_currentMacro = std::move(parsed).unwrap();
        s_currentMacro.name = std::string(name);
        s_hasMacro = true;
        return Ok();
    }

    Result<void> MacroManager::save() {
        if (!s_hasMacro) {
            return Err(xenia::tr("No active macro loaded", "Tidak ada makro aktif yang dimuat"));
        }
        s_currentMacro.modified = std::chrono::duration_cast<std::chrono::seconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
        return serializeToFile(macroPath(s_currentMacro.name), s_currentMacro);
    }

    bool MacroManager::hasCurrent() {
        return s_hasMacro;
    }

    Macro& MacroManager::current() {
        return s_currentMacro;
    }

    std::string MacroManager::info() {
        if (!s_hasMacro) {
            return xenia::tr("No macro loaded", "Belum ada makro yang dimuat");
        }
        int const last = s_currentMacro.frames.empty() ? 0 : s_currentMacro.frames.back().frame;
        double const sec = s_currentMacro.tps > 0 ? static_cast<double>(last) / s_currentMacro.tps : 0.0;
        return fmt::format(
            "Name: {}\nInputs: {}\nSamples: {}\nTPS: {:.1f}\nDuration: {:.2f}s (frame {})",
            s_currentMacro.name,
            s_currentMacro.frames.size(),
            s_currentMacro.samples.size(),
            s_currentMacro.tps,
            sec,
            last
        );
    }
}
