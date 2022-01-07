package cn.chuanwise.xiaoming.lexicons.pro.data;

import cn.chuanwise.toolkit.preservable.AbstractPreservable;
import cn.chuanwise.util.CollectionUtil;
import cn.chuanwise.util.MapUtil;
import lombok.Data;

import java.util.*;

@Data
public class LexiconManager extends AbstractPreservable {
    Set<LexiconEntry> globalEntries = new HashSet<>();
    Map<String, Set<LexiconEntry>> groupEntries = new HashMap<>();
    Map<Long, Set<LexiconEntry>> personalEntries = new HashMap<>();

    public Optional<LexiconEntry> forGlobalEntry(String input) {
        return forEntry(globalEntries, input);
    }

    public Optional<LexiconEntry> forGroupEntry(String tag, String input) {
        final Set<LexiconEntry> lexiconEntries = groupEntries.get(tag);
        if (CollectionUtil.isEmpty(lexiconEntries)) {
            return Optional.empty();
        }
        return forEntry(lexiconEntries, input);
    }

    public Optional<LexiconEntry> forPersonalEntry(long code, String input) {
        final Set<LexiconEntry> lexiconEntries = personalEntries.get(code);
        if (CollectionUtil.isEmpty(lexiconEntries)) {
            return Optional.empty();
        }
        return forEntry(lexiconEntries, input);
    }

    public Set<LexiconEntry> forGlobalEntres(String input) {
        return forEntries(globalEntries, input);
    }

    public Set<LexiconEntry> forGroupEntries(String tag, String input) {
        final Set<LexiconEntry> lexiconEntries = groupEntries.get(tag);
        if (CollectionUtil.isEmpty(lexiconEntries)) {
            return null;
        }
        return forEntries(lexiconEntries, input);
    }

    public Optional<Set<LexiconEntry>> forGroupEntries(String tag) {
        return Optional.ofNullable(groupEntries.get(tag));
    }

    public Optional<Set<LexiconEntry>> forPersonalEntries(long code, String input) {
        final Set<LexiconEntry> lexiconEntries = personalEntries.get(code);
        if (CollectionUtil.isEmpty(lexiconEntries)) {
            return Optional.empty();
        }
        return Optional.of(forEntries(lexiconEntries, input));
    }

    public Set<LexiconEntry> forPersonalEntries(long code) {
        return personalEntries.get(code);
    }

    public void removeGlobalEntry(LexiconEntry entry) {
        globalEntries.remove(entry);
    }

    public void removeGroupEntry(String tag, LexiconEntry entry) {
        final Set<LexiconEntry> lexiconEntries = groupEntries.get(tag);
        if (CollectionUtil.isEmpty(lexiconEntries)) {
            return;
        }
        lexiconEntries.remove(entry);
        if (lexiconEntries.isEmpty()) {
            globalEntries.remove(tag);
        }
    }

    public void removePersonalEntry(long code, LexiconEntry entry) {
        final Set<LexiconEntry> lexiconEntries = personalEntries.get(code);
        if (CollectionUtil.isEmpty(lexiconEntries)) {
            return;
        }
        lexiconEntries.remove(entry);
        if (lexiconEntries.isEmpty()) {
            personalEntries.remove(code);
        }
    }

    public void addGlobalEntry(LexiconEntry entry) {
        globalEntries.add(entry);
    }

    public void addGroupEntry(String tag, LexiconEntry entry) {
        final Set<LexiconEntry> lexiconEntries = MapUtil.getOrPutSupply(groupEntries, tag, HashSet::new);
        lexiconEntries.add(entry);
    }

    public void addPersonalEntry(long code, LexiconEntry entry) {
        final Set<LexiconEntry> lexiconEntries = MapUtil.getOrPutSupply(personalEntries, code, HashSet::new);
        lexiconEntries.add(entry);
    }


    protected Optional<LexiconEntry> forEntry(Set<LexiconEntry> entries, String input) {
        for (LexiconEntry lexiconEntry : entries) {
            if (lexiconEntry.apply(input).isPresent()) {
                return Optional.of(lexiconEntry);
            }
        }
        return Optional.empty();
    }

    protected Set<LexiconEntry> forEntries(Set<LexiconEntry> entries, String input) {
        final Set<LexiconEntry> matchedentries = new HashSet<>();
        for (LexiconEntry lexiconEntry : entries) {
            if (Objects.nonNull(lexiconEntry.apply(input))) {
                matchedentries.add(lexiconEntry);
            }
        }
        return matchedentries;
    }
}