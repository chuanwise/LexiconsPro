package cn.chuanwise.xiaoming.lexicons.pro.data;

import cn.chuanwise.utility.CollectionUtility;
import cn.chuanwise.xiaoming.lexicons.pro.LexiconsProPlugin;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.Objects;

public enum LexiconMatchType {
    START_EQUAL,
    START_MATCH,
    END_EQUAL,
    END_MATCH,
    EQUAL,
    EQUAL_IGNORE_CASE,
    MATCH,
    PARAMETER,
    CONTAIN_EQUAL,
    CONTAIN_MATCH;

    public static final BidiMap<LexiconMatchType, String> CHINESE_CONVERTOR = new DualHashBidiMap<>();
    static {
        CHINESE_CONVERTOR.put(START_MATCH, "开头匹配");
        CHINESE_CONVERTOR.put(END_MATCH, "结尾匹配");
        CHINESE_CONVERTOR.put(MATCH, "匹配");
        CHINESE_CONVERTOR.put(EQUAL_IGNORE_CASE, "忽略字母大小写匹配");
        CHINESE_CONVERTOR.put(CONTAIN_EQUAL, "包含");
        CHINESE_CONVERTOR.put(END_EQUAL, "结尾相等");
        CHINESE_CONVERTOR.put(PARAMETER, "参数提取");
        CHINESE_CONVERTOR.put(EQUAL, "相等");
        CHINESE_CONVERTOR.put(START_EQUAL, "开头相等");
        CHINESE_CONVERTOR.put(CONTAIN_MATCH, "包含匹配");
    }

    public String toChinese() {
        final String result = CHINESE_CONVERTOR.get(this);
        if (Objects.isNull(result)) {
            LexiconsProPlugin.INSTANCE.throwUnsupportedVersionException("matcherType: " + this + ", " +
                    "only types in {" + CollectionUtility.toString(CHINESE_CONVERTOR.keySet(), ", ") + "} are supported.", null);
        }
        return result;
    }

    public static LexiconMatchType fromChinese(String chinese) {
        final LexiconMatchType result = CHINESE_CONVERTOR.getKey(chinese);
        if (Objects.isNull(result)) {
            LexiconsProPlugin.INSTANCE.throwUnsupportedVersionException("matcherType: " + chinese + ", " +
                    "only chinese names in {" + CollectionUtility.toString(CHINESE_CONVERTOR.values(), ", ") + "} are supported.", null);
        }
        return result;
    }
}