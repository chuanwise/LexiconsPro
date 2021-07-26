package cn.chuanwise.xiaoming.lexicons.pro.interactor;

import cn.chuanwise.annotation.Uncomplete;
import cn.chuanwise.utility.CheckUtility;
import cn.chuanwise.utility.CollectionUtility;
import cn.chuanwise.utility.StringUtility;
import cn.chuanwise.xiaoming.annotation.*;
import cn.chuanwise.xiaoming.contact.message.Message;
import cn.chuanwise.xiaoming.user.GroupXiaomingUser;
import cn.chuanwise.xiaoming.user.XiaomingUser;
import cn.chuanwise.xiaoming.utility.CommandWords;
import cn.chuanwise.xiaoming.utility.InteractorUtility;
import cn.chuanwise.xiaoming.utility.MiraiCodeUtility;
import cn.chuanwise.xiaoming.interactor.InteractorImpl;
import cn.chuanwise.xiaoming.lexicons.pro.LexiconsProPlugin;
import cn.chuanwise.xiaoming.lexicons.pro.configuration.LexiconConfiguration;
import cn.chuanwise.xiaoming.lexicons.pro.data.LexiconManager;
import cn.chuanwise.xiaoming.lexicons.pro.data.LexiconMatchType;
import cn.chuanwise.xiaoming.lexicons.pro.data.LexiconMatcher;
import cn.chuanwise.xiaoming.lexicons.pro.data.LexiconEntry;
import net.mamoe.mirai.message.data.Image;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LexiconInteractor extends InteractorImpl {
    final LexiconsProPlugin plugin;
    final LexiconConfiguration configuration;
    final LexiconManager manager;

    static final String LEXICON = "(词库|lexicon)";
    static final String ENTRY = "(词条|entry)";
    static final String ALL = "(所有|all)";
    static final String EQUAL = "(相等|equals|equal)";
    static final String IGNORE = "(忽略|ignore)";
    static final String CASE = "(大小写|case)";
    static final String IGNORE_CASE = IGNORE + CASE;
    static final String MATCH = "(匹配|matches|match)";
    static final String START = "(开头|首|头|start|head)";
    static final String END = "(结尾|尾|end)";
    static final String RULE = "(规则|rule)";
    static final String CONTAIN = "(包含|含有|有|contain|contains)";
    static final String PARAMETER = "(参数|parameter|argument)";
    static final String REPLY = "(回复|reply)";
    static final String IMAGE = "(图片|image)";
    static final String MOVE = "(转移|move)";
    static final String COPY = "(复制|分支|fork|copy)";
    static final String BATCH = "(批量|bat|batch)";

    public LexiconInteractor(LexiconsProPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getLexiconManager();
        this.configuration = plugin.getConfiguration();

        setUsageCommandFormat(LEXICON + CommandWords.HELP);
    }

    @NonNext
    @Filter(ENTRY + RULE)
    @Permission("lexicons.matchType")
    public void onMatchType(XiaomingUser user) {
        user.sendMessage("当前版本的词库插件支持的匹配规则有：\n" +
                "相等：当触发词和输入相等时回复；类似地，还有「开头相等」、「结尾相等」和「忽略大小写相等」；\n" +
                "匹配：当输入匹配触发词的正则表达式时回复；类似地，还有「开头匹配」和「结尾匹配」；\n" +
                "包含：当输入包含触发词时回复；共分两种「包含相等」和「包含匹配」；\n" +
                "参数：当输入符合触发词的参数时，提取其中的参数并替换随机回复。例如，触发词「禁止{what}」，回复「禁止禁止{what}」，则发送「禁止复读」时会回复「禁止禁止复读」");
    }

    @Uncomplete
    private void onForkEntry(XiaomingUser user,
                             String key,
                             String fromLexiconType,
                             String toLexiconType,
                             Runnable fork) {
        user.sendMessage("你确定要复制" + fromLexiconType + "「" + key + "」到" + toLexiconType + "吗？");
    }

    private void addGlobalEntry(XiaomingUser user,
                                LexiconMatchType matchType,
                                String key, String reply) {
        addEntry(user, matchType, key, reply, "全局词条", () -> manager.forGlobalEntry(key), manager::addGlobalEntry);
    }

    private void addPersonalEntry(XiaomingUser user,
                                  LexiconMatchType matchType,
                                  String key, String reply) {
        final long code = user.getCode();
        addEntry(user, matchType, key, reply, "私人词条", () -> manager.forPersonalEntry(code, key), entry -> manager.addPersonalEntry(code, entry));
    }

    private void addGroupEntry(XiaomingUser user,
                               String groupTag,
                               LexiconMatchType matchType,
                               String key, String reply) {
        addEntry(user, matchType, key, reply, "群聊词条", () -> manager.forGroupEntry(groupTag, key), entry -> manager.addGroupEntry(groupTag, entry));
    }

    private void addEntry(XiaomingUser user,
                          LexiconMatchType matchType,
                          String key, String reply,
                          String lexiconType,
                          Supplier<LexiconEntry> onFindEntry,
                          Consumer<LexiconEntry> onAddNewEntry) {
        LexiconEntry entry = onFindEntry.get();
        if (Objects.isNull(entry)) {
            entry = new LexiconEntry();
        } else {
            user.sendMessage("已经存在" + lexiconType + "「" + key + "」了。\n" +
                    "你可以使用「删除" + lexiconType + "  " + key + "」删除该词条后再次添加，" +
                    "或使用「添加" + lexiconType + "回复  能触发该词条的话  <添加新的回答>」");
            return;
        }

        final LexiconMatcher matcher = new LexiconMatcher(matchType, key);
        entry.addMatcher(matcher);
        entry.addReply(reply);
        getXiaomingBot().getFileSaver().planToSave(manager);

        asyncSaveImages(MiraiCodeUtility.getImages(key));
        asyncSaveImages(MiraiCodeUtility.getImages(reply));

        user.sendMessage("成功创建新的" + lexiconType + "：" + matcher + " => {remain}");
        onAddNewEntry.accept(entry);
    }

    private List<Image> asyncSaveImages(Collection<Image> images) {
        final List<Image> failures = new CopyOnWriteArrayList<>();

        for (Image image : images) {
            getXiaomingBot().getScheduler().run(() -> {
                try {
                    getXiaomingBot().getResourceManager().saveImage(image);
                } catch (IOException exception) {
                    exception.printStackTrace();
                    failures.add(image);
                }
            });
        }

        return failures;
    }

    private void addEntryReply(XiaomingUser user,
                               String key, String reply,
                               String lexiconType,
                               Supplier<LexiconEntry> onFindEntry) {
        final LexiconEntry entry = onFindEntry.get();
        if (Objects.isNull(entry)) {
            user.sendMessage("没有找到" + lexiconType + "「" + key + "」\n" +
                    "先使用「添加" + lexiconType +"  <关键词>  <回复>」添加一个吧！");
            return;
        }

        // 检查是否已有匹配规则和回复
        if (entry.getReplies().contains(reply)) {
            user.sendWarning("现有的" + lexiconType + "「" + key + "」已有这条随机回复了。" +
                    "你可以使用「" + lexiconType + "  " + key + "」查看该词条的详细信息。");
            return;
        }

        entry.addReply(reply);
        getXiaomingBot().getFileSaver().planToSave(manager);

        asyncSaveImages(MiraiCodeUtility.getImages(key));
        asyncSaveImages(MiraiCodeUtility.getImages(reply));

        user.sendMessage("成功在现有的" + lexiconType + "「" + key + "」中" +
                "添加了新的随机回复「" + reply + "」，" +
                "该词条已有 " + entry.getReplies().size() + " 条随机回复");
    }

    private void addGlobalEntryReplyOneByOne(XiaomingUser user, String key) {
        addEntryReplyOneByOne(user, key, "全局词条", () -> manager.forGlobalEntry(key));
    }

    private void addGroupEntryReplyOneByOne(XiaomingUser user, String groupTag, String key) {
        addEntryReplyOneByOne(user, key, "群聊词条", () -> manager.forGroupEntry(groupTag, key));
    }

    private void addPersonalEntryReplyOneByOne(XiaomingUser user, String key) {
        addEntryReplyOneByOne(user, key, "全局词条", () -> manager.forPersonalEntry(user.getCode(), key));
    }

    private void addEntryReplyOneByOne(XiaomingUser user,
                               String key,
                               String lexiconType,
                               Supplier<LexiconEntry> onFindEntry) {
        final LexiconEntry entry = onFindEntry.get();
        if (Objects.isNull(entry)) {
            user.sendMessage("没有找到" + lexiconType + "「" + key + "」\n" +
                    "先使用「添加" + lexiconType +"  <关键词>  <回复>」添加一个吧！");
            return;
        }

        final Set<String> replies = entry.getReplies();

        // 一条一条输入词条回复
        final ArrayList<String> newReplies =
                InteractorUtility.fillStringCollection(user, "你希望在" + lexiconType + "「" + key + "」中添加哪些回复", "词条回复", new ArrayList<>(), true);

        for (String newReply : newReplies) {
            asyncSaveImages(MiraiCodeUtility.getImages(newReply));
            replies.add(newReply);
        }

        if (newReplies.isEmpty()) {
            user.sendWarning("本次没有添加任何回复");
        } else {
            user.sendMessage("成功在现有的" + lexiconType + "「" + key + "」中" +
                    "添加了 " + newReplies.size() + " 条随机回复，" +
                    "该词条已有 " + replies.size() + " 条随机回复");
            getXiaomingBot().getFileSaver().planToSave(manager);
        }
    }

    private void addEntryImageReply(XiaomingUser user,
                                    LexiconEntry entry,
                                    String key, String reply,
                                    String lexiconType) {
        final List<Image> images = MiraiCodeUtility.getImages(reply);
        if (images.isEmpty()) {
            user.sendMessage("没有在当前词条回复中找到任何图片 (；′⌒`)");
            return;
        }

        // 添加词条图片
        for (Image image : images) {
            entry.getReplies().add(image.serializeToMiraiCode());
        }

        // 启动多线程下载图片
        asyncSaveImages(images);

        user.sendMessage("成功在" + lexiconType + "「" + key + "」中添加了 " + images.size() + " 张图片，" +
                "现该词条下有 " + entry.getReplies().size() + " 条随机回复");
    }

    private void addGlobalEntryImageReply(XiaomingUser user, LexiconEntry entry, String key, String reply) {
        addEntryImageReply(user, entry, key ,reply, "全局词条");
    }

    private void addPersonalEntryImageReply(XiaomingUser user, LexiconEntry entry, String key, String reply) {
        addEntryImageReply(user, entry, key ,reply, "私人词条");
    }

    private void addGroupEntryImageReply(XiaomingUser user, LexiconEntry entry, String key, String reply) {
        addEntryImageReply(user, entry, key ,reply, "群聊词条");
    }

    private void addGlobalEntryReply(XiaomingUser user, String key, String reply) {
        addEntryReply(user, key, reply, "全局词条", () -> manager.forGlobalEntry(key));
    }

    private void addPersonalEntryReply(XiaomingUser user, String key, String reply) {
        addEntryReply(user, key, reply, "私人词条", () -> manager.forPersonalEntry(user.getCode(), key));
    }

    private void addGroupEntryReply(XiaomingUser user, String groupTag, String key, String reply) {
        addEntryReply(user, key, reply, "全局词条", () -> manager.forGroupEntry(groupTag, key));
    }

    private void removeGlobalEntryRule(XiaomingUser user, LexiconEntry entry) {
        removeEntryRule(user, entry, "全局词条", manager::removeGlobalEntry);
    }

    private void removePersonalEntryRule(XiaomingUser user, LexiconEntry entry) {
        removeEntryRule(user, entry, "私人词条", e -> manager.removePersonalEntry(user.getCode(), e));
    }

    private void removeGroupEntryRule(XiaomingUser user, String groupTag, LexiconEntry entry) {
        removeEntryRule(user, entry, "群聊词条", e -> manager.removeGroupEntry(groupTag, e));
    }

    private void removeEntryRule(XiaomingUser user, LexiconEntry entry, String lexiconType, Consumer<LexiconEntry> onRemoveEntry) {
        final Set<LexiconMatcher> matchers = entry.getMatchers();

        if (matchers.size() == 1) {
            user.sendMessage("该" + lexiconType + "中只有一个匹配规则「" + matchers.iterator().next() + "」，你确定要删除该规则并删除整个词条吗？" +
                    "如果是，请告诉我「确定」。其他任何回答将放弃本次删除行为");

            if (Objects.equals(user.nextInput().serialize(), "确定")) {
                user.sendMessage("成功删除该" + lexiconType + "中的唯一的匹配规则「" + matchers.iterator().next() + "」。" +
                        "因其不再具备任何匹配规则，整个词条也被一并删除");
                onRemoveEntry.accept(entry);
                getXiaomingBot().getFileSaver().planToSave(manager);
            } else {
                user.sendMessage("成功放弃本次删除行为");
            }
            return;
        }

        user.sendMessage("该" + lexiconType + "有很多匹配规则，希望删除哪一个呢？告诉小明它的序号吧");
        final LexiconMatcher lexiconMatcher = InteractorUtility.indexChooser(user, Arrays.asList(matchers.toArray(new LexiconMatcher[0])), 10);
        matchers.remove(lexiconMatcher);

        user.sendMessage("成功删除该" + lexiconType + "的匹配规则「" + lexiconMatcher + "」。" +
                "其还剩 " + matchers.size() + " 条匹配规则。");

        getXiaomingBot().getFileSaver().planToSave(manager);
    }

    private void removeEntry(XiaomingUser user, LexiconEntry entry, String key, String lexiconType, Consumer<LexiconEntry> onRemoveEntry) {
        user.sendMessage("成功删除" + lexiconType + "「" + key + "」，其详细信息：\n" + entry);
        onRemoveEntry.accept(entry);
        getXiaomingBot().getFileSaver().planToSave(manager);
    }

    private void removeGlobalEntry(XiaomingUser user, LexiconEntry entry, String key) {
        removeEntry(user, entry, key, "全局词条", manager::removeGlobalEntry);
    }

    private void removePersonalEntry(XiaomingUser user, LexiconEntry entry, String key) {
        removeEntry(user, entry, key, "私人词条", e -> manager.removePersonalEntry(user.getCode(), e));
    }

    private void removeGroupEntry(XiaomingUser user, String groupTag, LexiconEntry entry, String key) {
        removeEntry(user, entry, key, "群聊词条", e -> manager.removeGroupEntry(groupTag, e));
    }

    private void removeGroupEntryReply(XiaomingUser user, String groupTag, LexiconEntry entry, String key, String reply) {
        removeEntryReply(user, entry, key, reply, "群聊词条", e -> manager.removeGroupEntry(groupTag, entry));
    }

    private void removeGlobalEntryReply(XiaomingUser user, LexiconEntry entry, String key, String reply) {
        removeEntryReply(user, entry, key, reply, "全局词条", e -> manager.removeGlobalEntry(entry));
    }

    private void removePersonalEntryReply(XiaomingUser user, LexiconEntry entry, String key, String reply) {
        removeEntryReply(user, entry, key, reply, "私人词条", e -> manager.removePersonalEntry(user.getCode(), entry));
    }

    private void removeGroupEntryReplyIndex(XiaomingUser user, String groupTag, LexiconEntry entry, String key) {
        removeEntryReplyIndex(user, entry, key, "群聊词条", e -> manager.removeGroupEntry(groupTag, entry));
    }

    private void removeGlobalEntryReplyIndex(XiaomingUser user, LexiconEntry entry, String key) {
        removeEntryReplyIndex(user, entry, key, "全局词条", e -> manager.removeGlobalEntry(entry));
    }

    private void removePersonalEntryReplyIndex(XiaomingUser user, LexiconEntry entry, String key) {
        removeEntryReplyIndex(user, entry, key, "私人词条", e -> manager.removePersonalEntry(user.getCode(), entry));
    }

    private void removeEntryReply(XiaomingUser user, LexiconEntry entry, String key, String reply, String lexiconType, Consumer<LexiconEntry> onRemoveEntry) {
        if (!entry.getReplies().contains(reply)) {
            user.sendMessage(lexiconType + "「" + key + "」中并没有随机回答「" + reply + "」");
            return;
        }

        if (entry.getReplies().size() == 1) {
            user.sendMessage(lexiconType + "「" + key + "」中的只有一个随机回答「" + entry.getReplies().iterator().next() + "」，你确定要删除该回复并删除整个词条吗？" +
                    "如果是，请告诉我「确定」。其他任何回答将放弃本次删除行为");

            if (Objects.equals(user.nextInput().serialize(), "确定")) {
                user.sendMessage("成功删除" + lexiconType + "「" + key + "」中的唯一的随机回答「" + entry.getReplies().iterator().next() + "」。" +
                        "因其不再具备任何随机回答，整个词条也被一并删除");
                onRemoveEntry.accept(entry);
                getXiaomingBot().getFileSaver().planToSave(manager);
            } else {
                user.sendMessage("成功放弃本次删除行为");
            }
            return;
        }

        entry.getReplies().remove(reply);
        getXiaomingBot().getFileSaver().planToSave(manager);

        if (entry.getReplies().isEmpty()) {
            onRemoveEntry.accept(entry);
            user.sendMessage("成功删除" +lexiconType + "「" + key + "」中的随机回答「" + reply + "」。" +
                    "因其不再具备任何随机回答，整个词条也被一并删除");
        } else {
            user.sendMessage("成功删除" +lexiconType + "「" + key + "」中的随机回答「" + reply + "」。" +
                    "该词条还有 " + entry.getReplies().size() + " 个随机回答");
        }
    }

    private void removeEntryReplyIndex(XiaomingUser user, LexiconEntry entry, String key, String lexiconType, Consumer<LexiconEntry> onRemoveEntry) {
        if (entry.getReplies().size() == 1) {
            user.sendMessage(lexiconType + "「" + key + "」中的只有一个随机回答「" + entry.getReplies().iterator().next() + "」，你确定要删除该回复并删除整个词条吗？" +
                    "如果是，请告诉我「确定」。其他任何回答将放弃本次删除行为");

            if (Objects.equals(user.nextInput().serialize(), "确定")) {
                user.sendMessage("成功删除" + lexiconType + "「" + key + "」中的唯一的随机回答「" + entry.getReplies().iterator().next() + "」。" +
                        "因其不再具备任何随机回答，整个词条也被一并删除");
                onRemoveEntry.accept(entry);
                getXiaomingBot().getFileSaver().planToSave(manager);
            } else {
                user.sendMessage("成功放弃本次删除行为");
            }
            return;
        }

        final String reply = InteractorUtility.indexChooser(user, Arrays.asList(entry.getReplies().toArray(new String[0])), 10);
        entry.getReplies().remove(reply);
        user.sendMessage("成功删除" +lexiconType + "「" + key + "」中的随机回答「" + reply + "」。" +
                "该词条还有 " + entry.getReplies().size() + " 个随机回答");

        getXiaomingBot().getFileSaver().planToSave(manager);
    }

    @NonNext
    @WhenQuiet
    @WhenExternal
    @Filter(value = "", pattern = FilterPattern.STARTS_WITH, enableUsage = false)
    public boolean onMessage(XiaomingUser user, Message message) {
        final String serializedMessage = message.serialize();
        LexiconEntry entry = null;

        // 如果是群聊，是否开启了安静模式
        boolean shouldQuiet = false;
        if (user instanceof GroupXiaomingUser) {
            shouldQuiet = ((GroupXiaomingUser) user).getContact().hasTag(getXiaomingBot().getConfiguration().getQuietModeGroupTag());
        }

        // 寻找匹配的词条
        do {
            // 先寻找私人词库
            if (!shouldQuiet) {
                entry = manager.forPersonalEntry(user.getCode(), serializedMessage);
                if (Objects.nonNull(entry)) {
                    break;
                }
            }

            // 寻找群聊词库
            if (user instanceof GroupXiaomingUser) {
                final GroupXiaomingUser groupXiaomingUser = (GroupXiaomingUser) user;

                // 查找在所有 tag 群的词库
                for (String tag : groupXiaomingUser.getContact().getTags()) {
                    entry = manager.forGroupEntry(tag, serializedMessage);
                    if (Objects.nonNull(entry)) {
                        break;
                    }
                }
                if (Objects.nonNull(entry)) {
                    break;
                }
            }

            // 寻找全局词库
            entry = manager.forGlobalEntry(serializedMessage);
        } while (false);

        // 没有找到词条，不计入调用
        if (Objects.isNull(entry)) {
            return false;
        } else if (!user.hasPermission("lexicons.interact")) {
            user.sendMessage("你还不能调用词条哦，因为你缺少权限：lexicons.interact");
            return false;
        }

        // 判断回复是否为空
        final String reply = entry.apply(serializedMessage);
        if (StringUtility.isEmpty(reply)) {
            throw new IllegalStateException("ansewer for input: " + serializedMessage + " is empty string!");
        }

        user.getContact().send(reply);
        return true;
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGlobalEntry(user, LexiconMatchType.EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + IGNORE_CASE + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + IGNORE_CASE + ENTRY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalEqualIgnoreCaseEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGlobalEntry(user, LexiconMatchType.EQUAL_IGNORE_CASE, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + START + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + START + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalStartEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGlobalEntry(user, LexiconMatchType.START_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + END + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + END + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalEndEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGlobalEntry(user, LexiconMatchType.END_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGlobalEntry(user, LexiconMatchType.MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + START + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + START + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalStartMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGlobalEntry(user, LexiconMatchType.START_MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + END + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + END + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalEndMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGlobalEntry(user, LexiconMatchType.END_MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + PARAMETER + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + PARAMETER + ENTRY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalParameterEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关参数的匹配规则中不能包含图片");
            return;
        }
        try {
            addGlobalEntry(user, LexiconMatchType.PARAMETER, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + CONTAIN + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + CONTAIN + ENTRY + " {key} {remain}")
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + CONTAIN + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + CONTAIN + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalContainEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGlobalEntry(user, LexiconMatchType.CONTAIN_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + CONTAIN + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + CONTAIN + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalContainMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGlobalEntry(user, LexiconMatchType.CONTAIN_MATCH, key, reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.GLOBAL + ENTRY + " {globalEntry}")
    @Permission("lexicons.global.look")
    public void onLookGlobalEntry(XiaomingUser user, @FilterParameter("globalEntry") LexiconEntry entry) {
        user.sendMessage("【全局词条详情】：\n" + entry);
    }

    @NonNext
    @Filter(CommandWords.GLOBAL + ENTRY)
    @Filter(CommandWords.GLOBAL + LEXICON)
    @Permission("lexicons.global.list")
    public void onListGlobalEntry(XiaomingUser user) {
        final Set<LexiconEntry> globalEntries = manager.getGlobalEntries();
        if (CollectionUtility.isEmpty(globalEntries)) {
            user.sendWarning("没有任何全局词条");
        } else {
            user.sendMessage("共有 " + globalEntries.size() + " 个词条：\n" +
                    CollectionUtility.toIndexString(globalEntries, lexiconEntry -> CollectionUtility.toString(lexiconEntry.getMatchers(), "、")));
        }
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.GLOBAL + ENTRY + " {globalEntry}")
    @Permission("lexicons.global.remove")
    public void onRemoveGlobalEntry(XiaomingUser user, @FilterParameter("globalEntry") LexiconEntry entry, @FilterParameter("globalEntry") String key) {
        removeGlobalEntry(user, entry, key);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.GLOBAL + ENTRY + RULE + " {globalEntry}")
    @Permission("lexicons.global.remove")
    public void onRemoveGlobalEntryRule(XiaomingUser user, @FilterParameter("globalEntry") LexiconEntry entry) {
        removeGlobalEntryRule(user, entry);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.GLOBAL + ENTRY + REPLY + " {globalEntry} {remain}")
    @Permission("lexicons.global.remove")
    public void onRemoveGlobalEntryReply(XiaomingUser user, @FilterParameter("globalEntry") LexiconEntry entry, @FilterParameter("globalEntry") String key, @FilterParameter("remain") String reply) {
        removeGlobalEntryReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.GLOBAL + ENTRY + REPLY + " {globalEntry}")
    @Permission("lexicons.global.remove")
    public void onRemoveGlobalEntryReplyIndex(XiaomingUser user, @FilterParameter("globalEntry") LexiconEntry entry, @FilterParameter("globalEntry") String key) {
        removeGlobalEntryReplyIndex(user, entry, key);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + ENTRY + REPLY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + ENTRY + REPLY + " {key} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalEntryReply(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGlobalEntryReply(user, key, reply);
    }

    @NonNext
    @Filter(BATCH + CommandWords.ADD + CommandWords.GLOBAL + ENTRY + REPLY + " {key}")
    @Filter(BATCH + CommandWords.NEW + CommandWords.GLOBAL + ENTRY + REPLY + " {key}")
    @Permission("lexicons.global.add")
    public void onAddGlobalEntryReplyOneByOne(XiaomingUser user, @FilterParameter("key") String key) {
        addGlobalEntryReplyOneByOne(user, key);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GLOBAL + ENTRY + IMAGE + REPLY + " {globalEntry} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GLOBAL + ENTRY + IMAGE + REPLY + " {globalEntry} {remain}")
    @Permission("lexicons.global.add")
    public void onAddGlobalEntryImageReply(XiaomingUser user,
                                           @FilterParameter("globalEntry") String key,
                                           @FilterParameter("globalEntry") LexiconEntry entry,
                                           @FilterParameter("remain") String reply) {
        addGlobalEntryImageReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + ENTRY + IMAGE + REPLY + " {personalEntry} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + ENTRY + IMAGE + REPLY + " {personalEntry} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalEntryImageReply(XiaomingUser user,
                                           @FilterParameter("personalEntry") String key,
                                           @FilterParameter("personalEntry") LexiconEntry entry,
                                           @FilterParameter("remain") String reply) {
        addPersonalEntryImageReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addPersonalEntry(user, LexiconMatchType.EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + IGNORE_CASE + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + IGNORE_CASE + ENTRY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalEqualIgnoreCaseEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addPersonalEntry(user, LexiconMatchType.EQUAL_IGNORE_CASE, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + START + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + START + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalStartEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addPersonalEntry(user, LexiconMatchType.START_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + END + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + END + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalEndEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addPersonalEntry(user, LexiconMatchType.END_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addPersonalEntry(user, LexiconMatchType.MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + START + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + START + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalStartMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addPersonalEntry(user, LexiconMatchType.START_MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + END + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + END + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalEndMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addPersonalEntry(user, LexiconMatchType.END_MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + PARAMETER + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + PARAMETER + ENTRY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalParameterEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关参数的匹配规则中不能包含图片");
            return;
        }
        try {
            addPersonalEntry(user, LexiconMatchType.PARAMETER, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + CONTAIN + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + CONTAIN + ENTRY + " {key} {remain}")
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + CONTAIN + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + CONTAIN + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalContainEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addPersonalEntry(user, LexiconMatchType.CONTAIN_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + CONTAIN + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + CONTAIN + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalContainMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addPersonalEntry(user, LexiconMatchType.CONTAIN_MATCH, key, reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.PERSONAL + ENTRY + " {personalEntry}")
    @Permission("lexicons.personal.look")
    public void onLookPersonalEntry(XiaomingUser user, @FilterParameter("personalEntry") LexiconEntry entry) {
        user.sendMessage("【私人词条详情】：\n" + entry);
    }

    @NonNext
    @Filter(CommandWords.PERSONAL + ENTRY)
    @Filter(CommandWords.PERSONAL + LEXICON)
    @Permission("lexicons.personal.list")
    public void onListPersonalEntry(XiaomingUser user) {
        final Set<LexiconEntry> personalEntries = manager.forPersonalEntries(user.getCode());
        if (CollectionUtility.isEmpty(personalEntries)) {
            user.sendWarning("没有任何私人词条");
        } else {
            user.sendMessage("共有 " + personalEntries.size() + " 个词条：\n" +
                    CollectionUtility.toIndexString(personalEntries, lexiconEntry -> CollectionUtility.toString(lexiconEntry.getMatchers(), "、")));
        }
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.PERSONAL + ENTRY + " {personalEntry}")
    @Permission("lexicons.personal.remove")
    public void onRemovePersonalEntry(XiaomingUser user, @FilterParameter("personalEntry") LexiconEntry entry, @FilterParameter("personalEntry") String key) {
        removePersonalEntry(user, entry, key);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.PERSONAL + ENTRY + RULE + " {personalEntry}")
    @Permission("lexicons.personal.remove")
    public void onRemovePersonalEntryRule(XiaomingUser user, @FilterParameter("personalEntry") LexiconEntry entry) {
        removePersonalEntryRule(user, entry);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.PERSONAL + ENTRY + REPLY + " {personalEntry} {remain}")
    @Permission("lexicons.personal.remove")
    public void onRemovePersonalEntryReply(XiaomingUser user, @FilterParameter("personalEntry") LexiconEntry entry, @FilterParameter("personalEntry") String key, @FilterParameter("remain") String reply) {
        removePersonalEntryReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.PERSONAL + ENTRY + REPLY + " {personalEntry}")
    @Permission("lexicons.personal.remove")
    public void onRemovePersonalEntryReplyIndex(XiaomingUser user, @FilterParameter("personalEntry") LexiconEntry entry, @FilterParameter("personalEntry") String key) {
        removePersonalEntryReplyIndex(user, entry, key);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.PERSONAL + ENTRY + REPLY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.PERSONAL + ENTRY + REPLY + " {key} {remain}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalEntryReply(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addPersonalEntryReply(user, key, reply);
    }

    @NonNext
    @Filter(BATCH + CommandWords.ADD + CommandWords.PERSONAL + ENTRY + REPLY + " {key}")
    @Filter(BATCH + CommandWords.NEW + CommandWords.PERSONAL + ENTRY + REPLY + " {key}")
    @Permission("lexicons.personal.add")
    public void onAddPersonalEntryReplyOneByOne(XiaomingUser user, @FilterParameter("key") String key) {
        addPersonalEntryReplyOneByOne(user, key);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + ENTRY + IMAGE + REPLY + " {groupTag} {groupEntry} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + ENTRY + IMAGE + REPLY + " {groupTag} {groupEntry} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupEntryImageReply(XiaomingUser user,
                                             @FilterParameter("groupEntry") String key,
                                             @FilterParameter("groupEntry") LexiconEntry entry,
                                             @FilterParameter("remain") String reply) {
        addGroupEntryImageReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.ADD + CommandWords.GROUP + EQUAL + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + EQUAL + ENTRY + " {groupTag} {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        addGroupEntry(user, groupTag, LexiconMatchType.EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + START + EQUAL + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + START + EQUAL + ENTRY + " {groupTag} {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupStartEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        addGroupEntry(user, groupTag, LexiconMatchType.START_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + END + EQUAL + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + END + EQUAL + ENTRY + " {groupTag} {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupEndEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        addGroupEntry(user, groupTag, LexiconMatchType.END_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + MATCH + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + MATCH + ENTRY + " {groupTag} {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, groupTag, LexiconMatchType.MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + START + MATCH + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + START + MATCH + ENTRY + " {groupTag} {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupStartMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, groupTag, LexiconMatchType.START_MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + END + MATCH + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + END + MATCH + ENTRY + " {groupTag} {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupEndMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, groupTag, LexiconMatchType.END_MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + PARAMETER + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + PARAMETER + ENTRY + " {groupTag} {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupParameterEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关参数的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, groupTag, LexiconMatchType.PARAMETER, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + CONTAIN + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + CONTAIN + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.ADD + CommandWords.GROUP + CONTAIN + EQUAL + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + CONTAIN + EQUAL + ENTRY + " {groupTag} {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupContainEqualEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        addGroupEntry(user, groupTag, LexiconMatchType.CONTAIN_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + CONTAIN + MATCH + ENTRY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + CONTAIN + MATCH + ENTRY + " {groupTag} {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupContainMatchEntry(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, groupTag, LexiconMatchType.CONTAIN_MATCH, key, reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.GROUP + ENTRY + " {groupTag} {groupEntry}")
    @Permission("lexicons.group.{groupTag}.look")
    public void onLookGroupEntry(XiaomingUser user, @FilterParameter("groupTag") String groupTag, @FilterParameter("groupEntry") LexiconEntry entry) {
        user.sendMessage("【群聊词条详情】：\n" + entry);
    }

    @NonNext
    @Filter(CommandWords.GROUP + ENTRY + " {groupTag}")
    @Filter(CommandWords.GROUP + LEXICON + " {groupTag}")
    @Permission("lexicons.group.{groupTag}.list")
    public void onListGroupEntry(XiaomingUser user, @FilterParameter("groupTag") String groupTag) {
        final Set<LexiconEntry> groupEntries = manager.forGroupEntries(groupTag);
        if (CollectionUtility.isEmpty(groupEntries)) {
            user.sendWarning("没有任何群聊词条");
        } else {
            user.sendMessage("共有 " + groupEntries.size() + " 个词条：\n" +
                    CollectionUtility.toIndexString(groupEntries, lexiconEntry -> CollectionUtility.toString(lexiconEntry.getMatchers(), "、")));
        }
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.GROUP + ENTRY + " {groupTag} {groupEntry}")
    @Permission("lexicons.group.{groupTag}.remove")
    public void onRemoveGroupEntry(XiaomingUser user, @FilterParameter("groupTag") String groupTag, @FilterParameter("groupEntry") LexiconEntry entry, @FilterParameter("groupEntry") String key) {
        removeGroupEntry(user, groupTag, entry, key);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.GROUP + ENTRY + RULE + " {groupTag} {groupEntry}")
    @Permission("lexicons.group.{groupTag}.remove")
    public void onRemoveGroupEntryRule(XiaomingUser user, @FilterParameter("groupTag") String groupTag, @FilterParameter("groupEntry") LexiconEntry entry) {
        removeGroupEntryRule(user, groupTag, entry);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.GROUP + ENTRY + REPLY + " {groupTag} {groupEntry} {remain}")
    @Permission("lexicons.group.{groupTag}.remove")
    public void onRemoveGroupEntryReply(XiaomingUser user, @FilterParameter("groupEntry") LexiconEntry entry, @FilterParameter("groupEntry") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        removeGroupEntryReply(user, groupTag, entry, key, reply);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.GROUP + ENTRY + REPLY + " {groupTag} {groupEntry}")
    @Permission("lexicons.group.{groupTag}.remove")
    public void onRemoveGroupEntryReplyIndex(XiaomingUser user, @FilterParameter("groupEntry") LexiconEntry entry, @FilterParameter("groupTag") String groupTag, @FilterParameter("groupEntry") String key) {
        removeGroupEntryReplyIndex(user, groupTag, entry, key);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.GROUP + ENTRY + REPLY + " {groupTag} {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.GROUP + ENTRY + REPLY + " {groupTag} {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupEntryReply(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag, @FilterParameter("remain") String reply) {
        addGroupEntryReply(user, groupTag, key, reply);
    }

    @NonNext
    @Filter(BATCH + CommandWords.ADD + CommandWords.GROUP + ENTRY + REPLY + " {groupTag} {key}")
    @Filter(BATCH + CommandWords.NEW + CommandWords.GROUP + ENTRY + REPLY + " {groupTag} {key}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddGroupEntryReply(XiaomingUser user, @FilterParameter("key") String key, @FilterParameter("groupTag") String groupTag) {
        addGroupEntryReplyOneByOne(user, groupTag, key);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + ENTRY + IMAGE + REPLY + " {groupEntry} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + ENTRY + IMAGE + REPLY + " {groupEntry} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddGroupEntryImageReply(GroupXiaomingUser user,
                                          @FilterParameter("groupEntry") String key,
                                          @FilterParameter("groupEntry") LexiconEntry entry,
                                          @FilterParameter("remain") String reply) {
        addGroupEntryImageReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + ENTRY + " {key} {remain}")
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddThisGroupEqualEntry(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + IGNORE_CASE + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + IGNORE_CASE + ENTRY + " {key} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddThisGroupEqualIgnoreCaseEntry(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.EQUAL_IGNORE_CASE, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + START + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + START + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddThisGroupStartEqualEntry(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.START_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + END + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + END + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddThisGroupEndEqualEntry(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.END_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddThisGroupMatchEntry(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + START + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + START + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddThisGroupStartMatchEntry(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.START_MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + END + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + END + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddThisGroupEndMatchEntry(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.END_MATCH, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + PARAMETER + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + PARAMETER + ENTRY + " {key} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddThisGroupParameterEntry(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关参数的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.PARAMETER, MiraiCodeUtility.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + CONTAIN + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + CONTAIN + ENTRY + " {key} {remain}")
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + CONTAIN + EQUAL + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + CONTAIN + EQUAL + ENTRY + " {key} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddThisGroupContainEqualEntry(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.CONTAIN_EQUAL, key, reply);
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + CONTAIN + MATCH + ENTRY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + CONTAIN + MATCH + ENTRY + " {key} {remain}")
    @Permission("lexicons.group.{group}.add")
    public void onAddThisGroupContainMatchEntry(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        if (!MiraiCodeUtility.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.CONTAIN_MATCH, key, reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{key}」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(CommandWords.THIS + CommandWords.GROUP + ENTRY + " {groupEntry}")
    @Permission("lexicons.group.{group}.look")
    public void onLookThisGroupEntry(GroupXiaomingUser user, @FilterParameter("groupEntry") LexiconEntry entry) {
        user.sendMessage("【群聊词条详情】：\n" + entry);
    }

    @NonNext
    @Filter(CommandWords.THIS + CommandWords.GROUP + ENTRY)
    @Filter(CommandWords.THIS + CommandWords.GROUP + LEXICON)
    @Permission("lexicons.group.{group}.list")
    public void onListThisGroupEntry(GroupXiaomingUser user) {
        final Set<LexiconEntry> groupEntries = manager.forGroupEntries(user.getGroupCodeString());
        if (CollectionUtility.isEmpty(groupEntries)) {
            user.sendWarning("本群没有任何词条");
        } else {
            user.sendMessage("本群共有 " + groupEntries.size() + " 个词条：\n" +
                    CollectionUtility.toIndexString(groupEntries, lexiconEntry -> CollectionUtility.toString(lexiconEntry.getMatchers(), "、")));
        }
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.THIS + CommandWords.GROUP + ENTRY + " {groupEntry}")
    @Permission("lexicons.group.{group}.remove")
    public void onRemoveThisGroupEntry(GroupXiaomingUser user, @FilterParameter("groupEntry") LexiconEntry entry, @FilterParameter("groupEntry") String key) {
        removeGroupEntry(user, user.getGroupCodeString(), entry, key);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.THIS + CommandWords.GROUP + ENTRY + RULE + " {groupEntry}")
    @Permission("lexicons.group.{group}.remove")
    public void onRemoveThisGroupEntryRule(GroupXiaomingUser user, @FilterParameter("groupEntry") LexiconEntry entry) {
        removeGroupEntryRule(user, user.getGroupCodeString(), entry);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.THIS + CommandWords.GROUP + ENTRY + REPLY + " {groupEntry} {remain}")
    @Permission("lexicons.group.{group}.remove")
    public void onRemoveThisGroupEntryReply(GroupXiaomingUser user, @FilterParameter("groupEntry") LexiconEntry entry, @FilterParameter("groupEntry") String key, @FilterParameter("remain") String reply) {
        removeGroupEntryReply(user, user.getGroupCodeString(), entry, key, reply);
    }

    @NonNext
    @Filter(CommandWords.REMOVE + CommandWords.THIS + CommandWords.GROUP + ENTRY + REPLY + " {groupEntry}")
    @Permission("lexicons.group.{group}.remove")
    public void onRemoveThisGroupEntryReplyIndex(GroupXiaomingUser user, @FilterParameter("groupEntry") LexiconEntry entry, @FilterParameter("groupEntry") String key) {
        removeGroupEntryReplyIndex(user, user.getGroupCodeString(), entry, key);
    }

    @NonNext
    @Filter(ALL + CommandWords.GROUP + ENTRY)
    @Filter(ALL + CommandWords.GROUP + LEXICON)
    @Permission("lexicons.group")
    public void onListAllGroupEntry(XiaomingUser user) {
        final Map<String, Set<LexiconEntry>> groupEntries = manager.getGroupEntries();
        if (groupEntries.isEmpty()) {
            user.sendError("没有任何群具有群词条");
        } else {
            user.sendMessage("所有的群词条：\n" +
                    CollectionUtility.toIndexString(groupEntries.entrySet(), entry -> {
                        return entry.getKey() + "（" + getXiaomingBot().getGroupRecordManager().forTag(entry.getKey()).size() + " 个群）\n" +
                                CollectionUtility.toIndexString(entry.getValue(), (integer, element) -> ("(" + (integer + 1) + ") "),
                                        e -> CollectionUtility.toString(e.getMatchers(), "\n"), "\n");
                    }));
        }
    }

    @NonNext
    @Filter(CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + ENTRY + REPLY + " {key} {remain}")
    @Filter(CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + ENTRY + REPLY + " {key} {remain}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddThisGroupEntryReply(GroupXiaomingUser user, @FilterParameter("key") String key, @FilterParameter("remain") String reply) {
        addGroupEntryReply(user, user.getGroupCodeString(), key, reply);
    }

    @NonNext
    @Filter(BATCH + CommandWords.ADD + CommandWords.THIS + CommandWords.GROUP + ENTRY + REPLY + " {key}")
    @Filter(BATCH + CommandWords.NEW + CommandWords.THIS + CommandWords.GROUP + ENTRY + REPLY + " {key}")
    @Permission("lexicons.group.{groupTag}.add")
    public void onAddThisGroupEntryReplyOneByOne(GroupXiaomingUser user, @FilterParameter("key") String key) {
        addGroupEntryReplyOneByOne(user, user.getGroupCodeString(), key);
    }

    @Override
    public <T> T parseParameter(XiaomingUser user, Class<T> clazz, String parameterName, String currentValue, Map<String, String> argumentValues, String defaultValue) {
        final T t = super.parseParameter(user, clazz, parameterName, currentValue, argumentValues, defaultValue);
        if (Objects.nonNull(t)) {
            return t;
        }

        if (clazz.isAssignableFrom(LexiconEntry.class)) {
            switch (parameterName) {
                case "globalEntry": {
                    final LexiconEntry lexiconEntry = manager.forGlobalEntry(currentValue);
                    if (Objects.isNull(lexiconEntry)) {
                        user.sendError("并没有全局词条「" + currentValue + "」");
                        return null;
                    } else {
                        return ((T) lexiconEntry);
                    }
                }
                case "personalEntry": {
                    final LexiconEntry lexiconEntry = manager.forPersonalEntry(user.getCode(), currentValue);
                    if (Objects.isNull(lexiconEntry)) {
                        user.sendError("你没有私人词条「" + currentValue + "」哦");
                        return null;
                    } else {
                        return ((T) lexiconEntry);
                    }
                }
                case "groupEntry": {
                    final String groupTag;
                    if (Objects.nonNull(argumentValues) && argumentValues.containsKey("groupTag")) {
                        groupTag = argumentValues.get("groupTag");
                    } else {
                        CheckUtility.checkState(user instanceof GroupXiaomingUser, "user is not a instance of GroupXiaomingUser!");
                        groupTag = ((GroupXiaomingUser) user).getGroupCodeString();
                    }

                    final LexiconEntry lexiconEntry = manager.forGroupEntry(groupTag, currentValue);
                    if (Objects.isNull(lexiconEntry)) {
                        user.sendError("「" + groupTag + "」群中没有词条「" + currentValue + "」");
                        return null;
                    } else {
                        return ((T) lexiconEntry);
                    }
                }
                default:
            }
        }
        return null;
    }
}