package cn.chuanwise.xiaoming.lexicons.pro.interactors;

import cn.chuanwise.toolkit.container.Container;
import cn.chuanwise.util.CollectionUtil;
import cn.chuanwise.util.ConditionUtil;
import cn.chuanwise.xiaoming.annotation.*;
import cn.chuanwise.xiaoming.interactor.SimpleInteractors;
import cn.chuanwise.xiaoming.lexicons.pro.util.LexiconProWords;
import cn.chuanwise.xiaoming.user.GroupXiaomingUser;
import cn.chuanwise.xiaoming.user.XiaomingUser;
import cn.chuanwise.xiaoming.util.InteractorUtil;
import cn.chuanwise.xiaoming.util.MiraiCodeUtil;
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

public class LexiconsProInteractors extends SimpleInteractors<LexiconsProPlugin> {
    LexiconConfiguration configuration;
    LexiconManager lexiconManager;

    @Override
    public void onRegister() {
        this.lexiconManager = plugin.getLexiconManager();
        this.configuration = plugin.getConfiguration();

        xiaomingBot.getInteractorManager().registerParameterParser(LexiconEntry.class, context -> {
            final String inputValue = context.getInputValue();
            final String parameterName = context.getParameterName();
            final XiaomingUser user = context.getUser();

            switch (parameterName) {
                case "群词条":
                case "群聊词条":
                    final String groupTag;
                    if (Objects.nonNull(context.getArgument("群标签"))) {
                        groupTag = context.getArgument("群标签");
                    } else {
                        ConditionUtil.checkState(user instanceof GroupXiaomingUser, "user is not a instance of GroupXiaomingUser!");
                        groupTag = ((GroupXiaomingUser) user).getGroupCodeString();
                    }

                    final Optional<LexiconEntry> optionalGroupEntry = lexiconManager.forGroupEntry(groupTag, inputValue);
                    if (!optionalGroupEntry.isPresent()) {
                        user.sendError("「" + groupTag + "」群中没有词条「" + inputValue + "」");
                        return null;
                    } else {
                        return Container.of(optionalGroupEntry.get());
                    }
                case "私人词条":
                    final Optional<LexiconEntry> optionalPersonalEntry = lexiconManager.forPersonalEntry(user.getCode(), inputValue);
                    if (!optionalPersonalEntry.isPresent()) {
                        user.sendError("你没有私人词条「" + inputValue + "」哦");
                        return null;
                    } else {
                        return Container.of(optionalPersonalEntry.get());
                    }
                case "全局词条":
                    final Optional<LexiconEntry> optionalGlobalEntry = lexiconManager.forGlobalEntry(inputValue);
                    if (!optionalGlobalEntry.isPresent()) {
                        user.sendError("并没有全局词条「" + inputValue + "」");
                        return null;
                    } else {
                        return Container.of(optionalGlobalEntry.get());
                    }
                default:
                    return Container.empty();
            }
        }, true, plugin);
    }

//    @Incomplete
//    private void onForkEntry(XiaomingUser user,
//                             String key,
//                             String fromLexiconType,
//                             String toLexiconType,
//                             Runnable fork) {
//        user.sendMessage("你确定要复制" + fromLexiconType + "「" + key + "」到" + toLexiconType + "吗？" +
//                "回复「确定」以复制词条");
//        fork.run();
//    }

    private void addGlobalEntry(XiaomingUser user,
                                LexiconMatchType matchType,
                                String key, String reply) {
        addEntry(user, matchType, key, reply, "全局词条", () -> lexiconManager.forGlobalEntry(key), lexiconManager::addGlobalEntry);
    }

    private void addPersonalEntry(XiaomingUser user,
                                  LexiconMatchType matchType,
                                  String key, String reply) {
        final long code = user.getCode();
        addEntry(user, matchType, key, reply, "私人词条", () -> lexiconManager.forPersonalEntry(code, key), entry -> lexiconManager.addPersonalEntry(code, entry));
    }

    private void addGroupEntry(XiaomingUser user,
                               String groupTag,
                               LexiconMatchType matchType,
                               String key, String reply) {
        addEntry(user, matchType, key, reply, "群聊词条", () -> lexiconManager.forGroupEntry(groupTag, key), entry -> lexiconManager.addGroupEntry(groupTag, entry));
    }

    private void addEntry(XiaomingUser user,
                          LexiconMatchType matchType,
                          String key, String reply,
                          String lexiconType,
                          Supplier<Optional<LexiconEntry>> onFindEntry,
                          Consumer<LexiconEntry> onAddNewEntry) {
        final Optional<LexiconEntry> optionalEntry = onFindEntry.get();
        final LexiconEntry entry;
        if (!optionalEntry.isPresent()) {
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
        onAddNewEntry.accept(entry);
        getXiaomingBot().getFileSaver().readyToSave(lexiconManager);

        asyncSaveImages(MiraiCodeUtil.getImages(key));
        asyncSaveImages(MiraiCodeUtil.getImages(reply));

        user.sendMessage("成功创建新的" + lexiconType + "：" + matcher + " => " + reply);
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
                               Supplier<Optional<LexiconEntry>> onFindEntry) {
        final Optional<LexiconEntry> optionalEntry = onFindEntry.get();
        if (!optionalEntry.isPresent()) {
            user.sendMessage("没有找到" + lexiconType + "「" + key + "」\n" +
                    "先使用「添加" + lexiconType +"  <关键词>  <回复>」添加一个吧！");
            return;
        }
        final LexiconEntry entry = optionalEntry.get();

        // 检查是否已有匹配规则和回复
        if (entry.getReplies().contains(reply)) {
            user.sendWarning("现有的" + lexiconType + "「" + key + "」已有这条随机回复了。" +
                    "你可以使用「" + lexiconType + "  " + key + "」查看该词条的详细信息。");
            return;
        }

        entry.addReply(reply);
        getXiaomingBot().getFileSaver().readyToSave(lexiconManager);

        asyncSaveImages(MiraiCodeUtil.getImages(key));
        asyncSaveImages(MiraiCodeUtil.getImages(reply));

        user.sendMessage("成功在现有的" + lexiconType + "「" + key + "」中" +
                "添加了新的随机回复「" + reply + "」，" +
                "该词条已有 " + entry.getReplies().size() + " 条随机回复");
    }

    private void addGlobalEntryReplyOneByOne(XiaomingUser user, String key) {
        addEntryReplyOneByOne(user, key, "全局词条", () -> lexiconManager.forGlobalEntry(key));
    }

    private void addGroupEntryReplyOneByOne(XiaomingUser user, String groupTag, String key) {
        addEntryReplyOneByOne(user, key, "群聊词条", () -> lexiconManager.forGroupEntry(groupTag, key));
    }

    private void addPersonalEntryReplyOneByOne(XiaomingUser user, String key) {
        addEntryReplyOneByOne(user, key, "全局词条", () -> lexiconManager.forPersonalEntry(user.getCode(), key));
    }

    private void addEntryReplyOneByOne(XiaomingUser user,
                               String key,
                               String lexiconType,
                               Supplier<Optional<LexiconEntry>> onFindEntry) {
        final Optional<LexiconEntry> optionalEntry = onFindEntry.get();
        if (!optionalEntry.isPresent()) {
            user.sendMessage("没有找到" + lexiconType + "「" + key + "」\n" +
                    "先使用「添加" + lexiconType +"  <关键词>  <回复>」添加一个吧！");
            return;
        }
        final LexiconEntry entry = optionalEntry.get();

        final Set<String> replies = entry.getReplies();
        user.sendMessage("你希望在" + lexiconType + "「" + key + "」中添加哪些回复呢？{lang.inputItOneByOneAndEndsWithStop}");

        // 一条一条输入词条回复
        // 不直接往 replies 添加的原因是需要保存一下图片资源
        final ArrayList<String> newReplies =
                InteractorUtil.fillStringCollection(user, new ArrayList<>(),"词条回复");

        for (String newReply : newReplies) {
            asyncSaveImages(MiraiCodeUtil.getImages(newReply));
            replies.add(newReply);
        }

        if (newReplies.isEmpty()) {
            user.sendWarning("本次没有添加任何回复");
        } else {
            user.sendMessage("成功在现有的" + lexiconType + "「" + key + "」中" +
                    "添加了 " + newReplies.size() + " 条随机回复，" +
                    "该词条已有 " + replies.size() + " 条随机回复");
            getXiaomingBot().getFileSaver().readyToSave(lexiconManager);
        }
    }

    private void addEntryImageReply(XiaomingUser user,
                                    LexiconEntry entry,
                                    String key, String reply,
                                    String lexiconType) {
        final List<Image> images = MiraiCodeUtil.getImages(reply);
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
        addEntryReply(user, key, reply, "全局词条", () -> lexiconManager.forGlobalEntry(key));
    }

    private void addPersonalEntryReply(XiaomingUser user, String key, String reply) {
        addEntryReply(user, key, reply, "私人词条", () -> lexiconManager.forPersonalEntry(user.getCode(), key));
    }

    private void addGroupEntryReply(XiaomingUser user, String groupTag, String key, String reply) {
        addEntryReply(user, key, reply, "全局词条", () -> lexiconManager.forGroupEntry(groupTag, key));
    }

    private void removeGlobalEntryRule(XiaomingUser user, LexiconEntry entry) {
        removeEntryRule(user, entry, "全局词条", lexiconManager::removeGlobalEntry);
    }

    private void removePersonalEntryRule(XiaomingUser user, LexiconEntry entry) {
        removeEntryRule(user, entry, "私人词条", e -> lexiconManager.removePersonalEntry(user.getCode(), e));
    }

    private void removeGroupEntryRule(XiaomingUser user, String groupTag, LexiconEntry entry) {
        removeEntryRule(user, entry, "群聊词条", e -> lexiconManager.removeGroupEntry(groupTag, e));
    }

    private void removeEntryRule(XiaomingUser user, LexiconEntry entry, String lexiconType, Consumer<LexiconEntry> onRemoveEntry) {
        final Set<LexiconMatcher> matchers = entry.getMatchers();

        if (matchers.size() == 1) {
            user.sendMessage("该" + lexiconType + "中只有一个匹配规则「" + matchers.iterator().next() + "」，你确定要删除该规则并删除整个词条吗？" +
                    "如果是，请告诉我「确定」。其他任何回答将放弃本次删除行为");

            if (Objects.equals(user.nextMessageOrExit().serialize(), "确定")) {
                user.sendMessage("成功删除该" + lexiconType + "中的唯一的匹配规则「" + matchers.iterator().next() + "」。" +
                        "因其不再具备任何匹配规则，整个词条也被一并删除");
                onRemoveEntry.accept(entry);
                getXiaomingBot().getFileSaver().readyToSave(lexiconManager);
            } else {
                user.sendMessage("成功放弃本次删除行为");
            }
            return;
        }

        user.sendMessage("该" + lexiconType + "有很多匹配规则，希望删除哪一个呢？告诉小明它的序号吧");
        final LexiconMatcher lexiconMatcher = InteractorUtil.indexChooser(user, Arrays.asList(matchers.toArray(new LexiconMatcher[0])), 10);
        matchers.remove(lexiconMatcher);

        user.sendMessage("成功删除该" + lexiconType + "的匹配规则「" + lexiconMatcher + "」。" +
                "其还剩 " + matchers.size() + " 条匹配规则。");

        getXiaomingBot().getFileSaver().readyToSave(lexiconManager);
    }

    private void removeEntry(XiaomingUser user, LexiconEntry entry, String key, String lexiconType, Consumer<LexiconEntry> onRemoveEntry) {
        user.sendMessage("成功删除" + lexiconType + "「" + key + "」，其详细信息：\n" + entry);
        onRemoveEntry.accept(entry);
        getXiaomingBot().getFileSaver().readyToSave(lexiconManager);
    }

    private void removeGlobalEntry(XiaomingUser user, LexiconEntry entry, String key) {
        removeEntry(user, entry, key, "全局词条", lexiconManager::removeGlobalEntry);
    }

    private void removePersonalEntry(XiaomingUser user, LexiconEntry entry, String key) {
        removeEntry(user, entry, key, "私人词条", e -> lexiconManager.removePersonalEntry(user.getCode(), e));
    }

    private void removeGroupEntry(XiaomingUser user, String groupTag, LexiconEntry entry, String key) {
        removeEntry(user, entry, key, "群聊词条", e -> lexiconManager.removeGroupEntry(groupTag, e));
    }

    private void removeGroupEntryReply(XiaomingUser user, String groupTag, LexiconEntry entry, String key, String reply) {
        removeEntryReply(user, entry, key, reply, "群聊词条", e -> lexiconManager.removeGroupEntry(groupTag, entry));
    }

    private void removeGlobalEntryReply(XiaomingUser user, LexiconEntry entry, String key, String reply) {
        removeEntryReply(user, entry, key, reply, "全局词条", e -> lexiconManager.removeGlobalEntry(entry));
    }

    private void removePersonalEntryReply(XiaomingUser user, LexiconEntry entry, String key, String reply) {
        removeEntryReply(user, entry, key, reply, "私人词条", e -> lexiconManager.removePersonalEntry(user.getCode(), entry));
    }

    private void removeGroupEntryReplyIndex(XiaomingUser user, String groupTag, LexiconEntry entry, String key) {
        removeEntryReplyIndex(user, entry, key, "群聊词条", e -> lexiconManager.removeGroupEntry(groupTag, entry));
    }

    private void removeGlobalEntryReplyIndex(XiaomingUser user, LexiconEntry entry, String key) {
        removeEntryReplyIndex(user, entry, key, "全局词条", e -> lexiconManager.removeGlobalEntry(entry));
    }

    private void removePersonalEntryReplyIndex(XiaomingUser user, LexiconEntry entry, String key) {
        removeEntryReplyIndex(user, entry, key, "私人词条", e -> lexiconManager.removePersonalEntry(user.getCode(), entry));
    }

    private void removeEntryReply(XiaomingUser user, LexiconEntry entry, String key, String reply, String lexiconType, Consumer<LexiconEntry> onRemoveEntry) {
        if (!entry.getReplies().contains(reply)) {
            user.sendMessage(lexiconType + "「" + key + "」中并没有随机回答「" + reply + "」");
            return;
        }

        if (entry.getReplies().size() == 1) {
            user.sendMessage(lexiconType + "「" + key + "」中的只有一个随机回答「" + entry.getReplies().iterator().next() + "」，你确定要删除该回复并删除整个词条吗？" +
                    "如果是，请告诉我「确定」。其他任何回答将放弃本次删除行为");

            if (Objects.equals(user.nextMessageOrExit().serialize(), "确定")) {
                user.sendMessage("成功删除" + lexiconType + "「" + key + "」中的唯一的随机回答「" + entry.getReplies().iterator().next() + "」。" +
                        "因其不再具备任何随机回答，整个词条也被一并删除");
                onRemoveEntry.accept(entry);
                getXiaomingBot().getFileSaver().readyToSave(lexiconManager);
            } else {
                user.sendMessage("成功放弃本次删除行为");
            }
            return;
        }

        entry.getReplies().remove(reply);
        getXiaomingBot().getFileSaver().readyToSave(lexiconManager);

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

            if (Objects.equals(user.nextMessageOrExit().serialize(), "确定")) {
                user.sendMessage("成功删除" + lexiconType + "「" + key + "」中的唯一的随机回答「" + entry.getReplies().iterator().next() + "」。" +
                        "因其不再具备任何随机回答，整个词条也被一并删除");
                onRemoveEntry.accept(entry);
                getXiaomingBot().getFileSaver().readyToSave(lexiconManager);
            } else {
                user.sendMessage("成功放弃本次删除行为");
            }
            return;
        }

        final String reply = InteractorUtil.indexChooser(user, Arrays.asList(entry.getReplies().toArray(new String[0])), 10);
        entry.getReplies().remove(reply);
        user.sendMessage("成功删除" +lexiconType + "「" + key + "」中的随机回答「" + reply + "」。" +
                "该词条还有 " + entry.getReplies().size() + " 个随机回答");

        getXiaomingBot().getFileSaver().readyToSave(lexiconManager);
    }

    @NonNext
    @Filter(LexiconProWords.SAVE + LexiconProWords.LEXICON + LexiconProWords.CONFIGURE)
    @Filter(LexiconProWords.WRITE + LexiconProWords.LEXICON + LexiconProWords.CONFIGURE)
    @Required("lexicons.configuration.write")
    public void writeConfiguration(XiaomingUser user) {
        xiaomingBot.getFileSaver().readyToSave(configuration);
        user.sendMessage("成功保存词库配置");
    }

    @NonNext
    @Filter(LexiconProWords.SAVE + LexiconProWords.LEXICON)
    @Filter(LexiconProWords.WRITE + LexiconProWords.LEXICON)
    @Required("lexicons.data.write")
    public void writeData(XiaomingUser user) {
        xiaomingBot.getFileSaver().readyToSave(lexiconManager);
        user.sendMessage("成功保存词库数据");
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGlobalEntry(user, LexiconMatchType.EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.IGNORE_CASE + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.IGNORE_CASE + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalEqualIgnoreCaseEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGlobalEntry(user, LexiconMatchType.EQUAL_IGNORE_CASE, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.START + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.START + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalStartEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGlobalEntry(user, LexiconMatchType.START_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.END + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.END + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalEndEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGlobalEntry(user, LexiconMatchType.END_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGlobalEntry(user, LexiconMatchType.MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.START + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.START + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalStartMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGlobalEntry(user, LexiconMatchType.START_MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「{args.触发词}」有错误：{context.exception}，请仔细核对", exception);
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.END + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.END + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalEndMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGlobalEntry(user, LexiconMatchType.END_MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.PARAMETER + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.PARAMETER + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalParameterEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关参数的匹配规则中不能包含图片");
            return;
        }
        try {
            addGlobalEntry(user, LexiconMatchType.PARAMETER, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.CONTAIN + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.CONTAIN + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.CONTAIN + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.CONTAIN + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalContainEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGlobalEntry(user, LexiconMatchType.CONTAIN_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.CONTAIN + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.CONTAIN + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalContainMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGlobalEntry(user, LexiconMatchType.CONTAIN_MATCH, key, reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.GLOBAL + LexiconProWords.ENTRY + " {全局词条}")
    @Required("lexicons.global.look")
    public void onLookGlobalEntry(XiaomingUser user, @FilterParameter("全局词条") LexiconEntry entry) {
        user.sendMessage("【全局词条详情】：\n" + entry);
    }

    @NonNext
    @Filter(LexiconProWords.GLOBAL + LexiconProWords.ENTRY)
    @Filter(LexiconProWords.GLOBAL + LexiconProWords.LEXICON)
    @Required("lexicons.global.list")
    public void onListGlobalEntry(XiaomingUser user) {
        final Set<LexiconEntry> globalEntries = lexiconManager.getGlobalEntries();
        if (CollectionUtil.isEmpty(globalEntries)) {
            user.sendWarning("没有任何全局词条");
        } else {
            user.sendMessage("共有 " + globalEntries.size() + " 个词条：\n" +
                    CollectionUtil.toIndexString(globalEntries, lexiconEntry -> CollectionUtil.toString(lexiconEntry.getMatchers(), "、")));
        }
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + " {全局词条}")
    @Required("lexicons.global.remove")
    public void onRemoveGlobalEntry(XiaomingUser user, @FilterParameter("全局词条") LexiconEntry entry, @FilterParameter("全局词条") String key) {
        removeGlobalEntry(user, entry, key);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + LexiconProWords.RULE + " {全局词条}")
    @Required("lexicons.global.remove")
    public void onRemoveGlobalEntryRule(XiaomingUser user, @FilterParameter("全局词条") LexiconEntry entry) {
        removeGlobalEntryRule(user, entry);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {全局词条} {r:回复}")
    @Required("lexicons.global.remove")
    public void onRemoveGlobalEntryReply(XiaomingUser user, @FilterParameter("全局词条") LexiconEntry entry, @FilterParameter("全局词条") String key, @FilterParameter("回复") String reply) {
        removeGlobalEntryReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {全局词条}")
    @Required("lexicons.global.remove")
    public void onRemoveGlobalEntryReplyIndex(XiaomingUser user, @FilterParameter("全局词条") LexiconEntry entry, @FilterParameter("全局词条") String key) {
        removeGlobalEntryReplyIndex(user, entry, key);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalEntryReply(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGlobalEntryReply(user, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.BATCH + LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词}")
    @Filter(LexiconProWords.BATCH + LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词}")
    @Required("lexicons.global.add")
    public void onAddGlobalEntryReplyOneByOne(XiaomingUser user, @FilterParameter("触发词") String key) {
        addGlobalEntryReplyOneByOne(user, key);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + LexiconProWords.IMAGE + LexiconProWords.REPLY + " {全局词条} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GLOBAL + LexiconProWords.ENTRY + LexiconProWords.IMAGE + LexiconProWords.REPLY + " {全局词条} {r:回复}")
    @Required("lexicons.global.add")
    public void onAddGlobalEntryImageReply(XiaomingUser user,
                                           @FilterParameter("全局词条") String key,
                                           @FilterParameter("全局词条") LexiconEntry entry,
                                           @FilterParameter("回复") String reply) {
        addGlobalEntryImageReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + LexiconProWords.IMAGE + LexiconProWords.REPLY + " {私人词条} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + LexiconProWords.IMAGE + LexiconProWords.REPLY + " {私人词条} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalEntryImageReply(XiaomingUser user,
                                           @FilterParameter("私人词条") String key,
                                           @FilterParameter("私人词条") LexiconEntry entry,
                                           @FilterParameter("回复") String reply) {
        addPersonalEntryImageReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addPersonalEntry(user, LexiconMatchType.EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.IGNORE_CASE + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.IGNORE_CASE + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalEqualIgnoreCaseEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addPersonalEntry(user, LexiconMatchType.EQUAL_IGNORE_CASE, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.START + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.START + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalStartEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addPersonalEntry(user, LexiconMatchType.START_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.END + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.END + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalEndEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addPersonalEntry(user, LexiconMatchType.END_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addPersonalEntry(user, LexiconMatchType.MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.START + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.START + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalStartMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addPersonalEntry(user, LexiconMatchType.START_MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.END + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.END + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalEndMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addPersonalEntry(user, LexiconMatchType.END_MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.PARAMETER + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.PARAMETER + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalParameterEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关参数的匹配规则中不能包含图片");
            return;
        }
        try {
            addPersonalEntry(user, LexiconMatchType.PARAMETER, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.CONTAIN + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.CONTAIN + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.CONTAIN + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.CONTAIN + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalContainEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addPersonalEntry(user, LexiconMatchType.CONTAIN_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.CONTAIN + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.CONTAIN + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalContainMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addPersonalEntry(user, LexiconMatchType.CONTAIN_MATCH, key, reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.PERSONAL + LexiconProWords.ENTRY + " {私人词条}")
    @Required("lexicons.personal.look")
    public void onLookPersonalEntry(XiaomingUser user, @FilterParameter("私人词条") LexiconEntry entry) {
        user.sendMessage("【私人词条详情】：\n" + entry);
    }

    @NonNext
    @Filter(LexiconProWords.PERSONAL + LexiconProWords.ENTRY)
    @Filter(LexiconProWords.PERSONAL + LexiconProWords.LEXICON)
    @Required("lexicons.personal.list")
    public void onListPersonalEntry(XiaomingUser user) {
        final Set<LexiconEntry> personalEntries = lexiconManager.forPersonalEntries(user.getCode());
        if (CollectionUtil.isEmpty(personalEntries)) {
            user.sendWarning("没有任何私人词条");
        } else {
            user.sendMessage("共有 " + personalEntries.size() + " 个词条：\n" +
                    CollectionUtil.toIndexString(personalEntries, lexiconEntry -> CollectionUtil.toString(lexiconEntry.getMatchers(), "、")));
        }
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + " {私人词条}")
    @Required("lexicons.personal.remove")
    public void onRemovePersonalEntry(XiaomingUser user, @FilterParameter("私人词条") LexiconEntry entry, @FilterParameter("私人词条") String key) {
        removePersonalEntry(user, entry, key);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + LexiconProWords.RULE + " {私人词条}")
    @Required("lexicons.personal.remove")
    public void onRemovePersonalEntryRule(XiaomingUser user, @FilterParameter("私人词条") LexiconEntry entry) {
        removePersonalEntryRule(user, entry);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {私人词条} {r:回复}")
    @Required("lexicons.personal.remove")
    public void onRemovePersonalEntryReply(XiaomingUser user, @FilterParameter("私人词条") LexiconEntry entry, @FilterParameter("私人词条") String key, @FilterParameter("回复") String reply) {
        removePersonalEntryReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {私人词条}")
    @Required("lexicons.personal.remove")
    public void onRemovePersonalEntryReplyIndex(XiaomingUser user, @FilterParameter("私人词条") LexiconEntry entry, @FilterParameter("私人词条") String key) {
        removePersonalEntryReplyIndex(user, entry, key);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词} {r:回复}")
    @Required("lexicons.personal.add")
    public void onAddPersonalEntryReply(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addPersonalEntryReply(user, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.BATCH + LexiconProWords.ADD + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词}")
    @Filter(LexiconProWords.BATCH + LexiconProWords.NEW + LexiconProWords.PERSONAL + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词}")
    @Required("lexicons.personal.add")
    public void onAddPersonalEntryReplyOneByOne(XiaomingUser user, @FilterParameter("触发词") String key) {
        addPersonalEntryReplyOneByOne(user, key);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.IMAGE + LexiconProWords.REPLY + " {群标签} {群词条} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.IMAGE + LexiconProWords.REPLY + " {群标签} {群词条} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupEntryImageReply(XiaomingUser user,
                                             @FilterParameter("群词条") String key,
                                             @FilterParameter("群词条") LexiconEntry entry,
                                             @FilterParameter("回复") String reply) {
        addGroupEntryImageReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        addGroupEntry(user, groupTag, LexiconMatchType.EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.START + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.START + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupStartEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        addGroupEntry(user, groupTag, LexiconMatchType.START_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.END + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.END + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupEndEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        addGroupEntry(user, groupTag, LexiconMatchType.END_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, groupTag, LexiconMatchType.MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.START + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.START + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupStartMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, groupTag, LexiconMatchType.START_MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.END + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.END + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupEndMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, groupTag, LexiconMatchType.END_MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.PARAMETER + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.PARAMETER + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupParameterEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关参数的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, groupTag, LexiconMatchType.PARAMETER, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupContainEqualEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        addGroupEntry(user, groupTag, LexiconMatchType.CONTAIN_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {群标签} {触发词} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupContainMatchEntry(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, groupTag, LexiconMatchType.CONTAIN_MATCH, key, reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.GROUP + LexiconProWords.ENTRY + " {群标签} {群词条}")
    @Required("lexicons.group.{args.群标签}.look")
    public void onLookGroupEntry(XiaomingUser user, @FilterParameter("群标签") String groupTag, @FilterParameter("群词条") LexiconEntry entry) {
        user.sendMessage("【群聊词条详情】：\n" + entry);
    }

    @NonNext
    @Filter(LexiconProWords.GROUP + LexiconProWords.ENTRY + " {群标签}")
    @Filter(LexiconProWords.GROUP + LexiconProWords.LEXICON + " {群标签}")
    @Required("lexicons.group.{args.群标签}.list")
    public void onListGroupEntry(XiaomingUser user, @FilterParameter("群标签") String groupTag) {
        final Optional<Set<LexiconEntry>> optionalGroupEntries = lexiconManager.forGroupEntries(groupTag);
        if (!optionalGroupEntries.isPresent()) {
            user.sendWarning("没有任何群聊词条");
        } else {
            final Set<LexiconEntry> groupEntries = optionalGroupEntries.get();
            user.sendMessage("共有 " + groupEntries.size() + " 个词条：\n" +
                    CollectionUtil.toIndexString(groupEntries, lexiconEntry -> CollectionUtil.toString(lexiconEntry.getMatchers(), "、")));
        }
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.GROUP + LexiconProWords.ENTRY + " {群标签} {群词条}")
    @Required("lexicons.group.{args.群标签}.remove")
    public void onRemoveGroupEntry(XiaomingUser user,
                                   @FilterParameter("群标签") String groupTag,
                                   @FilterParameter("群词条") LexiconEntry entry,
                                   @FilterParameter("群词条") String key) {
        removeGroupEntry(user, groupTag, entry, key);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.RULE + " {群标签} {群词条}")
    @Required("lexicons.group.{args.群标签}.remove")
    public void onRemoveGroupEntryRule(XiaomingUser user, @FilterParameter("群标签") String groupTag, @FilterParameter("群词条") LexiconEntry entry) {
        removeGroupEntryRule(user, groupTag, entry);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {群标签} {群词条} {r:回复}")
    @Required("lexicons.group.{args.群标签}.remove")
    public void onRemoveGroupEntryReply(XiaomingUser user, @FilterParameter("群词条") LexiconEntry entry, @FilterParameter("群词条") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        removeGroupEntryReply(user, groupTag, entry, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {群标签} {群词条}")
    @Required("lexicons.group.{args.群标签}.remove")
    public void onRemoveGroupEntryReplyIndex(XiaomingUser user, @FilterParameter("群词条") LexiconEntry entry, @FilterParameter("群标签") String groupTag, @FilterParameter("群词条") String key) {
        removeGroupEntryReplyIndex(user, groupTag, entry, key);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {群标签} {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {群标签} {触发词} {r:回复}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupEntryReply(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag, @FilterParameter("回复") String reply) {
        addGroupEntryReply(user, groupTag, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.BATCH + LexiconProWords.ADD + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {群标签} {触发词}")
    @Filter(LexiconProWords.BATCH + LexiconProWords.NEW + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {群标签} {触发词}")
    @Required("lexicons.group.{args.群标签}.add")
    public void onAddGroupEntryReply(XiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("群标签") String groupTag) {
        addGroupEntryReplyOneByOne(user, groupTag, key);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.IMAGE + LexiconProWords.REPLY + " {群词条} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.IMAGE + LexiconProWords.REPLY + " {群词条} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddGroupEntryImageReply(GroupXiaomingUser user,
                                          @FilterParameter("群词条") String key,
                                          @FilterParameter("群词条") LexiconEntry entry,
                                          @FilterParameter("回复") String reply) {
        addGroupEntryImageReply(user, entry, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupEqualEntry(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.IGNORE_CASE + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.IGNORE_CASE + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupEqualIgnoreCaseEntry(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.EQUAL_IGNORE_CASE, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.START + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.START + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupStartEqualEntry(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.START_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.END + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.END + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupEndEqualEntry(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.END_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupMatchEntry(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.START + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.START + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupStartMatchEntry(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.START_MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.END + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.END + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupEndMatchEntry(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.END_MATCH, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.PARAMETER + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.PARAMETER + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupParameterEntry(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关参数的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.PARAMETER, MiraiCodeUtil.contentToString(key), reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.EQUAL + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupContainEqualEntry(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.CONTAIN_EQUAL, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.CONTAIN + LexiconProWords.MATCH + LexiconProWords.ENTRY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupContainMatchEntry(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        if (!MiraiCodeUtil.getImages(key).isEmpty()) {
            user.sendError("有关正则表达式的匹配规则中不能包含图片");
            return;
        }
        try {
            addGroupEntry(user, user.getGroupCodeString(), LexiconMatchType.CONTAIN_MATCH, key, reply);
        } catch (Exception exception) {
            user.sendError("正则表达式「" + key + "」有错误：" + exception + "，请仔细核对。");
        }
    }

    @NonNext
    @Filter(LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + " {群词条}")
    @Required("lexicons.group.{user.groupCode}.look")
    public void onLookThisGroupEntry(GroupXiaomingUser user, @FilterParameter("群词条") LexiconEntry entry) {
        user.sendMessage("【群聊词条详情】：\n" + entry);
    }

    @NonNext
    @Filter(LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY)
    @Filter(LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.LEXICON)
    @Required("lexicons.group.{user.groupCode}.list")
    public void onListThisGroupEntry(GroupXiaomingUser user) {
        final Set<LexiconEntry> groupEntries = new HashSet<>();
        user.getContact().getTags().forEach(tag -> lexiconManager.forGroupEntries(user.getGroupCodeString()).ifPresent(groupEntries::addAll));

        if (CollectionUtil.isEmpty(groupEntries)) {
            user.sendWarning("本群没有任何词条");
        } else {
            final Set<LexiconEntry> entries = CollectionUtil.copyOf(groupEntries);

            user.sendMessage("本群共有 " + groupEntries.size() + " 个词条：\n" +
                    CollectionUtil.toIndexString(groupEntries, lexiconEntry -> CollectionUtil.toString(lexiconEntry.getMatchers(), "、")));
        }
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + " {群词条}")
    @Required("lexicons.group.{user.groupCode}.remove")
    public void onRemoveThisGroupEntry(GroupXiaomingUser user, @FilterParameter("群词条") LexiconEntry entry, @FilterParameter("群词条") String key) {
        removeGroupEntry(user, user.getGroupCodeString(), entry, key);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.RULE + " {群词条}")
    @Required("lexicons.group.{user.groupCode}.remove")
    public void onRemoveThisGroupEntryRule(GroupXiaomingUser user, @FilterParameter("群词条") LexiconEntry entry) {
        removeGroupEntryRule(user, user.getGroupCodeString(), entry);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {群词条} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.remove")
    public void onRemoveThisGroupEntryReply(GroupXiaomingUser user, @FilterParameter("群词条") LexiconEntry entry, @FilterParameter("群词条") String key, @FilterParameter("回复") String reply) {
        removeGroupEntryReply(user, user.getGroupCodeString(), entry, key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.REMOVE + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {群词条}")
    @Required("lexicons.group.{user.groupCode}.remove")
    public void onRemoveThisGroupEntryReplyIndex(GroupXiaomingUser user, @FilterParameter("群词条") LexiconEntry entry, @FilterParameter("群词条") String key) {
        removeGroupEntryReplyIndex(user, user.getGroupCodeString(), entry, key);
    }

    @NonNext
    @Filter(LexiconProWords.ALL + LexiconProWords.GROUP + LexiconProWords.ENTRY)
    @Filter(LexiconProWords.ALL + LexiconProWords.GROUP + LexiconProWords.LEXICON)
    @Required("lexicons.group")
    public void onListAllGroupEntry(XiaomingUser user) {
        final Map<String, Set<LexiconEntry>> groupEntries = lexiconManager.getGroupEntries();
        if (groupEntries.isEmpty()) {
            user.sendError("没有任何群具有群词条");
        } else {
            user.sendMessage("所有的群词条：\n" +
                    CollectionUtil.toIndexString(groupEntries.entrySet(), entry -> {
                        return entry.getKey() + "（" + getXiaomingBot().getGroupInformationManager().searchGroupsByTag(entry.getKey()).size() + " 个群）\n" +
                                CollectionUtil.toIndexString(entry.getValue(), (integer, element) -> ("(" + (integer + 1) + ") "),
                                        e -> CollectionUtil.toString(e.getMatchers(), "\n"), "\n");
                    }));
        }
    }

    @NonNext
    @Filter(LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词} {r:回复}")
    @Filter(LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词} {r:回复}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupEntryReply(GroupXiaomingUser user, @FilterParameter("触发词") String key, @FilterParameter("回复") String reply) {
        addGroupEntryReply(user, user.getGroupCodeString(), key, reply);
    }

    @NonNext
    @Filter(LexiconProWords.BATCH + LexiconProWords.ADD + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词}")
    @Filter(LexiconProWords.BATCH + LexiconProWords.NEW + LexiconProWords.THIS + LexiconProWords.GROUP + LexiconProWords.ENTRY + LexiconProWords.REPLY + " {触发词}")
    @Required("lexicons.group.{user.groupCode}.add")
    public void onAddThisGroupEntryReplyOneByOne(GroupXiaomingUser user, @FilterParameter("触发词") String key) {
        addGroupEntryReplyOneByOne(user, user.getGroupCodeString(), key);
    }
}