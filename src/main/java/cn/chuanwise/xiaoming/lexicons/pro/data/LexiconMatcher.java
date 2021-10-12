package cn.chuanwise.xiaoming.lexicons.pro.data;

import cn.chuanwise.api.Flushable;
import cn.chuanwise.exception.UnsupportedVersionException;
import cn.chuanwise.pattern.ParameterPattern;
import cn.chuanwise.util.ConditionUtil;
import cn.chuanwise.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.beans.Transient;
import java.util.Objects;
import java.util.regex.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LexiconMatcher
        implements Flushable {
    LexiconMatchType matchType = LexiconMatchType.EQUAL;
    String content;

    transient ParameterPattern parameterPattern;
    transient Pattern pattern;

    public LexiconMatcher(LexiconMatchType matchType, String content) {
        this.matchType = matchType;
        this.content = content;

        flush();
    }

    public void setContent(String content) {
        this.content = content;
        flush();
    }

    @Transient
    public ParameterPattern getParameterPattern() {
        ConditionUtil.checkState(matchType == LexiconMatchType.PARAMETER, "can not call the method: getParameterFilterMatcher() " +
                "for a lexicon matcher without matchType equals to \"PARAMETER\"!");
        if (Objects.isNull(parameterPattern)) {
            parameterPattern = new ParameterPattern(content);
        }
        return parameterPattern;
    }

    @Transient
    public Pattern getPattern() {
        ConditionUtil.checkState(matchType == LexiconMatchType.START_MATCH ||
                matchType == LexiconMatchType.END_MATCH ||
                matchType == LexiconMatchType.CONTAIN_MATCH ||
                matchType == LexiconMatchType.MATCH, "can not call the method: getPattern() " +
                "for a lexicon matcher without matchType equals to \"START_MATCH\", \"END_MATCH\", \"CONTAIN_MATCH\" and \"MATCH\"!");
        if (Objects.isNull(pattern)) {
            pattern = Pattern.compile(content);
        }
        return pattern;
    }

    @Override
    public void flush() {
        if (matchType == LexiconMatchType.PARAMETER) {
            getParameterPattern();
        } else if (matchType == LexiconMatchType.START_MATCH ||
                matchType == LexiconMatchType.END_MATCH ||
                matchType == LexiconMatchType.MATCH) {
            getPattern();
        }
    }

    public boolean apply(String input) {
        switch (matchType) {
            case CONTAIN_EQUAL:
                return input.contains(content);

            case START_EQUAL:
                return input.startsWith(content);
            case END_EQUAL:
                return input.endsWith(content);
            case EQUAL:
                return Objects.equals(input, content);
            case EQUAL_IGNORE_CASE:
                return input.equalsIgnoreCase(content);

            case PARAMETER:
                return getParameterPattern().matches(input);

            case START_MATCH:
                return StringUtil.startMatches(input, pattern);
            case END_MATCH:
                return StringUtil.endMatches(input, pattern);
            case MATCH:
                return StringUtil.endMatches(input, pattern);

            case CONTAIN_MATCH:
                return pattern.matcher(input).find();

            default:
                throw new UnsupportedVersionException("matcherType: " + matchType);
        }
    }

    @Override
    public String toString() {
        return content + "（" + matchType.toChinese() + "）";
    }
}
