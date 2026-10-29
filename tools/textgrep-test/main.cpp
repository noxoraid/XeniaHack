#include <jni.h>

#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

#include "textgrep.h"

extern "C" jlongArray Java_com_uplink_app_TextGrep_nativeGrep(JNIEnv*, jobject, jbyteArray, jint, jbyteArray, jboolean, jint);

static int failures = 0;

#define CHECK(cond) do { if (!(cond)) { printf("FAIL %s:%d %s\n", __FILE__, __LINE__, #cond); failures++; } } while (0)

static std::vector<nx::GrepHit> run(const std::string& text, const std::string& key, bool icase, size_t max = 1000) {
    std::vector<nx::GrepHit> out;
    nx::grepLiteral(reinterpret_cast<const uint8_t*>(text.data()), text.size(),
                    reinterpret_cast<const uint8_t*>(key.data()), key.size(), icase, max, out);
    return out;
}

static jbyteArray bytesOf(const std::string& s) {
    jbyteArray a = new _jarray();
    a->bytes.assign(s.begin(), s.end());
    return a;
}

int main() {
    {
        auto h = run("alpha\nbeta\ngamma beta\n", "beta", false);
        CHECK(h.size() == 2);
        CHECK(h[0].line == 2 && h[0].start == 6 && h[0].end == 10);
        CHECK(h[1].line == 3 && h[1].start == 11 && h[1].end == 21);
    }
    {
        auto h = run("Beta\nBETA\nbeta", "beta", true);
        CHECK(h.size() == 3);
        auto s = run("Beta\nBETA\nbeta", "beta", false);
        CHECK(s.size() == 1 && s[0].line == 3);
    }
    {
        auto h = run("one\r\ntwo\r\n", "two", false);
        CHECK(h.size() == 1 && h[0].start == 5 && h[0].end == 8);
    }
    {
        auto h = run("no newline at end beta", "beta", false);
        CHECK(h.size() == 1 && h[0].line == 1 && h[0].end == 22);
    }
    CHECK(run("", "x", false).empty());
    CHECK(run("abc", "", false).empty());
    CHECK(run("abc", "abcd", false).empty());
    CHECK(run("abc", "abc", false, 0).empty());
    CHECK(run("aaaa\naaaa\naaaa", "aa", false, 2).size() == 2);
    CHECK(run("\n\n\n", "a", false).empty());
    CHECK(run("xbetx\nbet", "beta", true).empty());
    {
        std::string big;
        for (int i = 0; i < 20000; i++) big += "line number " + std::to_string(i) + " filler text\n";
        auto h = run(big, "number 19999 ", false);
        CHECK(h.size() == 1 && h[0].line == 20000);
        auto many = run(big, "FILLER", true, 50);
        CHECK(many.size() == 50);
    }
    {
        std::string bin("ab\0cd", 5);
        CHECK(nx::looksBinary(reinterpret_cast<const uint8_t*>(bin.data()), bin.size()));
        std::string txt("hello");
        CHECK(!nx::looksBinary(reinterpret_cast<const uint8_t*>(txt.data()), txt.size()));
        CHECK(!nx::looksBinary(nullptr, 0));
    }
    {
        JNIEnv env;
        std::string text = "foo\nbar foo\nbaz";
        jbyteArray data = bytesOf(text);
        jbyteArray key = bytesOf("FOO");
        jlongArray r = Java_com_uplink_app_TextGrep_nativeGrep(&env, nullptr, data, static_cast<jint>(text.size()), key, JNI_TRUE, 10);
        CHECK(r->longs.size() == 6);
        CHECK(r->longs[0] == 1 && r->longs[1] == 0 && r->longs[2] == 3);
        CHECK(r->longs[3] == 2 && r->longs[4] == 4);
        jlongArray none = Java_com_uplink_app_TextGrep_nativeGrep(&env, nullptr, data, 0, key, JNI_TRUE, 10);
        CHECK(none->longs.empty());
        jlongArray clipped = Java_com_uplink_app_TextGrep_nativeGrep(&env, nullptr, data, 9999, key, JNI_TRUE, 10);
        CHECK(clipped->longs.size() == 6);
        jlongArray nullKey = Java_com_uplink_app_TextGrep_nativeGrep(&env, nullptr, data, 5, nullptr, JNI_TRUE, 10);
        CHECK(nullKey->longs.empty());
        delete r; delete none; delete clipped; delete nullKey; delete data; delete key;
    }
    printf(failures ? "FAILED %d\n" : "OK\n", failures);
    return failures ? 1 : 0;
}
