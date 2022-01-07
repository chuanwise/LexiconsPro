package cn.chuanwise.xiaoming.lexicons.pro.data;

import cn.chuanwise.exception.UnsupportedVersionException;
import cn.chuanwise.util.*;
import cn.chuanwise.xiaoming.lexicons.pro.LexiconsProPlugin;
import lombok.Data;

import java.util.*;

@Data
public class LexiconEntry {
    Set<LexiconMatcher> matchers = new HashSet<>();
    Set<String> replies = new HashSet<>();

    boolean privateSend = false;

    protected static final String MATCHER = "matcher";

    public Optional<String> apply(String input) {
        final LexiconsProPlugin plugin = LexiconsProPlugin.INSTANCE;
        final String reply = replies.toArray(new String[0])[RandomUtil.nextInt(replies.size())];

        for (LexiconMatcher matcher : matchers) {
            if (matcher.apply(input)) {
                switch (matcher.getMatchType()) {
                    case END_MATCH:
                    case START_MATCH:
                    case CONTAIN_MATCH:
                        final String matchedPart = matcher.getPattern().matcher(input).group(1);
                        return Optional.of(plugin.getXiaomingBot().getLanguageManager().formatAdditional(reply, variable -> {
                            if (Objects.equals(matchedPart, "matcher")) {
                                return matchedPart;
                            }
                            return null;
                        }));
                    case START_EQUAL:
                    case EQUAL:
                    case END_EQUAL:
                    case CONTAIN_EQUAL:
                    case EQUAL_IGNORE_CASE:
                    case MATCH:
                        return Optional.of(reply);
                    case PARAMETER:
                        final Map<String, String> variableTable = matcher.getParameterPattern().parse(input).orElseThrow(NoSuchElementException::new);
                        return Optional.of(plugin.getXiaomingBot().getLanguageManager().formatAdditional(reply, variableTable::get));
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
