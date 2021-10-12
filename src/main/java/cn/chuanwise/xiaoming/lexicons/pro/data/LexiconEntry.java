package cn.chuanwise.xiaoming.lexicons.pro.data;

import cn.chuanwise.exception.UnsupportedVersionException;
import cn.chuanwise.util.*;
import cn.chuanwise.xiaoming.lexicons.pro.LexiconsProPlugin;
import lombok.Data;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Data
public class LexiconEntry {
    Set<LexiconMatcher> matchers = new HashSet<>();
    Set<String> replies = new HashSet<>();

    public Optional<String> apply(String input) {
        for (LexiconMatcher matcher : matchers) {
            if (matcher.apply(input)) {
                final String reply = replies.toArray(new String[0])[RandomUtil.nextInt(replies.size())];
                final LexiconMatchType matchType = matcher.getMatchType();

                switch (matchType) {
                    case START_EQUAL:
                    case EQUAL:
                    case END_EQUAL:
                    case CONTAIN_EQUAL:
                    case EQUAL_IGNORE_CASE:
                    case MATCH:
                    case END_MATCH:
                    case START_MATCH:
                        return Optional.of(reply);
                    case PARAMETER:
                        return Optional.of(ArgumentUtil.format(reply,
                                LexiconsProPlugin.INSTANCE.getConfiguration().getMaxIterateTime(),
                                matcher.getParameterPattern().parse(input).orElseThrow()));
                    default:
                        throw new UnsupportedVersionException();
                }
            }
        }
        return Optional.empty();
    }

    public void addMatcher(LexiconMatcher matcher) {
        matchers.add(matcher);
    }

    public void addReply(String reply) {
        replies.add(reply);
    }

    public void removeMatcher(LexiconMatcher matcher) {
        matchers.remove(matcher);
    }

    public void removeReply(String reply) {
        replies.remove(reply);
    }

    @Override
    public String toString() {
        return "匹配规则：" + ObjectUtil.getOrConduct(CollectionUtil.toIndexString(matchers), StringUtil::notEmpty, string -> ("\n" + string), "（无）") + "\n" +
                "回复：" + ObjectUtil.getOrConduct(CollectionUtil.toIndexString(replies), StringUtil::notEmpty, string -> ("\n" + string), "（无）");
    }
}
