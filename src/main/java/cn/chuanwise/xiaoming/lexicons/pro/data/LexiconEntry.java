package cn.chuanwise.xiaoming.lexicons.pro.data;

import cn.chuanwise.utility.*;
import cn.chuanwise.xiaoming.lexicons.pro.LexiconsProPlugin;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class LexiconEntry {
    Set<LexiconMatcher> matchers = new HashSet<>();
    Set<String> replies = new HashSet<>();

    public String apply(String input) {
        for (LexiconMatcher matcher : matchers) {
            if (matcher.apply(input)) {
                final String reply = replies.toArray(new String[0])[RandomUtility.nextInt(replies.size())];
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
                        return reply;
                    case PARAMETER:
                        return ArgumentUtility.replaceArguments(reply, matcher.getParameterFilterMatcher().getArgumentValues(input), LexiconsProPlugin.INSTANCE.getConfiguration().getMaxIterateTime());
                    default:
                        LexiconsProPlugin.INSTANCE.throwUnsupportedVersionException("matcherType: " + matchType);
                }
            }
        }
        return null;
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
        return "匹配规则：" + ObjectUtility.getOrConduct(CollectionUtility.toIndexString(matchers), StringUtility::nonEmpty, string -> ("\n" + string), "（无）") + "\n" +
                "回复：" + ObjectUtility.getOrConduct(CollectionUtility.toIndexString(replies), StringUtility::nonEmpty, string -> ("\n" + string), "（无）");
    }
}
