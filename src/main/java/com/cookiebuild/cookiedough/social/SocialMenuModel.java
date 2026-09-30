package com.cookiebuild.cookiedough.social;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.cookiebuild.cookiedough.social.SocialMenuAction.Kind;
import com.cookiebuild.cookiedough.social.SocialPage.Entry;
import com.cookiebuild.cookiedough.social.SocialPage.Icon;
import com.cookiebuild.cookiedough.social.SocialView.Friend;
import com.cookiebuild.cookiedough.social.SocialView.Presence;

/** Pure page builder: decides which buttons each screen offers from a {@link SocialView}. */
public final class SocialMenuModel {
    /** Keeps every list inside one 54-slot chest (45 content slots) with room for a "more" hint. */
    static final int MAX_LIST = 36;
    static final int MAX_HOME_FRIENDS = 18;

    @FunctionalInterface
    public interface Messages {
        String get(String key, Object... args);
    }

    private SocialMenuModel() { }

    public static SocialPage build(SocialPageId page, String context, SocialView view, String notice,
            Messages messages) {
        Builder builder = new Builder(messages);
        if (notice != null && !notice.isBlank()) builder.line(notice);
        String title = switch (page) {
            case HOME -> home(view, builder);
            case FRIENDS -> friends(view, builder);
            case FRIEND -> friend(view, context, builder);
            case REQUESTS -> requests(view, builder);
            case REQUEST -> request(view, context, builder);
            case ADD -> add(view, builder);
            case PARTY -> party(view, builder);
            case PARTY_INVITE -> partyInvite(view, builder);
            case CONFIRM_REMOVE -> confirmRemove(context, builder);
            case CONFIRM_LEAVE -> confirmLeave(builder);
        };
        return new SocialPage(page, title, builder.content, builder.entries);
    }

    private static String home(SocialView view, Builder b) {
        List<Friend> onServer = view.friendsOnServer();
        long online = view.friends().stream().filter(friend -> friend.presence() != Presence.OFFLINE).count();
        b.line(b.m.get("social.menu.summary", online, view.incoming().size()));
        if (view.party() == null) {
            for (SocialView.PartyInvite invite : view.partyInvites()) {
                b.button(b.m.get("social.menu.party_invite.name", invite.leaderName()),
                        b.m.get("social.menu.party_invite.lore"), Icon.PARTY_INVITE,
                        SocialMenuAction.of(Kind.JOIN_PARTY, invite.leaderId().toString()));
            }
        }
        if (!view.incoming().isEmpty()) {
            b.button(b.m.get("social.menu.requests.name", view.incoming().size()),
                    b.m.get("social.menu.requests.lore"), Icon.REQUEST, SocialMenuAction.of(Kind.REQUESTS));
        }
        if (onServer.isEmpty()) {
            b.line(b.m.get("social.menu.no_online_friends"));
        }
        onServer.stream().limit(MAX_HOME_FRIENDS).forEach(friend -> b.friendButton(friend));
        b.button(b.m.get("social.menu.add.name"), b.m.get("social.menu.add.lore"), Icon.ADD,
                SocialMenuAction.of(Kind.ADD));
        b.button(b.m.get("social.menu.party.name"), partySummary(view, b.m), Icon.PARTY,
                SocialMenuAction.of(Kind.PARTY));
        b.button(b.m.get("social.menu.friends.name", view.friends().size()),
                b.m.get("social.menu.friends.lore", online), Icon.FRIENDS, SocialMenuAction.of(Kind.FRIENDS));
        b.footer(b.m.get("social.menu.close"), Icon.CLOSE, SocialMenuAction.of(Kind.CLOSE));
        return b.m.get("social.menu.title");
    }

    private static String friends(SocialView view, Builder b) {
        if (view.friends().isEmpty()) {
            b.line(b.m.get("social.friends.empty"));
            b.button(b.m.get("social.menu.add.name"), b.m.get("social.menu.add.lore"), Icon.ADD,
                    SocialMenuAction.of(Kind.ADD));
        } else {
            List<Friend> sorted = view.friends().stream()
                    .sorted(Comparator.comparingInt((Friend friend) -> friend.presence().onServer() ? 0
                            : friend.presence() == Presence.ELSEWHERE ? 1 : 2)
                            .thenComparing(friend -> friend.name().toLowerCase(Locale.ROOT)))
                    .toList();
            sorted.stream().limit(MAX_LIST).forEach(b::friendButton);
            b.more(sorted.size());
        }
        b.back(SocialMenuAction.of(Kind.HOME));
        return b.m.get("social.friends.title");
    }

    private static String friend(SocialView view, String name, Builder b) {
        Friend friend = view.friend(name);
        if (friend == null) {
            b.line(b.m.get("social.friend.missing", name));
            b.back(SocialMenuAction.of(Kind.HOME));
            return name;
        }
        b.line(status(friend, b.m));
        if (friend.inMyParty()) b.line(b.m.get("social.status.in_your_party"));
        if (friend.presence() == Presence.OFFLINE) b.line(b.m.get("social.friend.offline_hint", friend.name()));
        if (friend.presence() == Presence.ELSEWHERE) b.line(b.m.get("social.friend.elsewhere", friend.name()));
        if (friend.presence().onServer() && !friend.inMyParty() && view.canInvite()) {
            b.button(b.m.get("social.friend.invite.name"), b.m.get("social.friend.invite.lore", friend.name()),
                    Icon.INVITE, SocialMenuAction.of(Kind.INVITE, friend.name()));
        }
        if (canFollow(view, friend)) {
            b.button(b.m.get("social.friend.join.name", friend.activity()),
                    friend.presence() == Presence.QUEUED
                            ? b.m.get("social.friend.join.lore_queued", friend.name())
                            : b.m.get("social.friend.join.lore_playing", friend.name()),
                    Icon.JOIN_QUEUE, SocialMenuAction.of(Kind.JOIN_QUEUE, friend.name()));
        }
        b.button(b.m.get("social.friend.remove.name"), b.m.get("social.friend.remove.lore"), Icon.REMOVE,
                SocialMenuAction.of(Kind.CONFIRM_REMOVE, friend.name()));
        b.back(SocialMenuAction.of(Kind.HOME));
        return friend.name();
    }

    /** "Join my friend's queue" is offered only toward a named activity the viewer is not already in. */
    static boolean canFollow(SocialView view, Friend friend) {
        return view.viewerCanQueue() && friend.presence().onServer() && friend.joinable()
                && !friend.activity().equalsIgnoreCase(view.viewerActivity());
    }

    private static String requests(SocialView view, Builder b) {
        if (view.incoming().isEmpty()) {
            b.line(b.m.get("social.requests.empty"));
        } else {
            b.line(b.m.get("social.requests.content"));
            view.incoming().stream().limit(MAX_LIST).forEach(name -> b.add(new Entry(name,
                    b.m.get("social.requests.entry_lore"), Icon.REQUEST,
                    SocialMenuAction.of(Kind.REQUEST, name), name, false, false)));
            b.more(view.incoming().size());
        }
        if (!view.outgoing().isEmpty()) {
            b.line(b.m.get("social.requests.sent", String.join(", ", view.outgoing())));
        }
        b.back(SocialMenuAction.of(Kind.HOME));
        return b.m.get("social.requests.title");
    }

    private static String request(SocialView view, String name, Builder b) {
        if (!view.hasIncoming(name)) {
            b.line(b.m.get("social.request.missing", name));
        } else {
            b.line(b.m.get("social.request.content", name));
            b.button(b.m.get("social.request.accept"), b.m.get("social.request.accept_lore", name), Icon.ACCEPT,
                    SocialMenuAction.of(Kind.ACCEPT, name));
            b.button(b.m.get("social.request.decline"), "", Icon.DENY, SocialMenuAction.of(Kind.DENY, name));
        }
        b.back(SocialMenuAction.of(Kind.REQUESTS));
        return b.m.get("social.request.title", name);
    }

    private static String add(SocialView view, Builder b) {
        List<String> candidates = addCandidates(view);
        b.line(b.m.get(candidates.isEmpty() ? "social.add.empty" : "social.add.content"));
        candidates.stream().limit(MAX_LIST).forEach(name -> b.add(new Entry(name,
                b.m.get(view.hasIncoming(name) ? "social.add.entry_lore_incoming" : "social.add.entry_lore"),
                Icon.PLAYER, SocialMenuAction.of(Kind.SEND_REQUEST, name), name, false, false)));
        b.more(candidates.size());
        if (view.bedrock()) {
            b.button(b.m.get("social.add.by_name.name"), b.m.get("social.add.by_name.lore"), Icon.TYPE_NAME,
                    SocialMenuAction.of(Kind.ADD_BY_NAME));
        } else {
            b.line(b.m.get("social.add.java_hint"));
        }
        b.back(SocialMenuAction.of(Kind.HOME));
        return b.m.get("social.add.title");
    }

    /** Online players the viewer could still befriend: not self, not a friend, not already asked. */
    static List<String> addCandidates(SocialView view) {
        Set<String> excluded = new java.util.HashSet<>();
        excluded.add(view.viewerName().toLowerCase(Locale.ROOT));
        view.friends().forEach(friend -> excluded.add(friend.name().toLowerCase(Locale.ROOT)));
        view.outgoing().forEach(name -> excluded.add(name.toLowerCase(Locale.ROOT)));
        return view.onlinePlayers().stream()
                .filter(name -> name != null && !excluded.contains(name.toLowerCase(Locale.ROOT)))
                .distinct()
                .sorted(Comparator.comparing((String name) -> !view.hasIncoming(name))
                        .thenComparing(name -> name.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static String party(SocialView view, Builder b) {
        SocialView.Party party = view.party();
        if (party == null) {
            b.line(b.m.get("social.party.content_none"));
            for (SocialView.PartyInvite invite : view.partyInvites()) {
                b.button(b.m.get("social.menu.party_invite.name", invite.leaderName()),
                        b.m.get("social.menu.party_invite.lore"), Icon.PARTY_INVITE,
                        SocialMenuAction.of(Kind.JOIN_PARTY, invite.leaderId().toString()));
            }
            b.button(b.m.get("social.party.invite.name"), b.m.get("social.party.invite.lore"), Icon.INVITE,
                    SocialMenuAction.of(Kind.PARTY_INVITE));
        } else {
            b.line(b.m.get("social.party.leader", party.leaderName()));
            b.line(b.m.get("social.party.members", party.members().size(), party.maxSize(),
                    party.members().stream()
                            .map(member -> member.online() ? member.name()
                                    : member.name() + " (" + b.m.get("social.status.offline") + ")")
                            .collect(Collectors.joining(", "))));
            for (SocialView.Member member : party.members()) {
                b.add(new Entry(member.name(),
                        b.m.get(member.leader() ? "social.party.member_leader" : "social.party.member")
                                + " · " + b.m.get(member.online() ? "social.status.online" : "social.status.offline"),
                        Icon.MEMBER, SocialMenuAction.of(Kind.NOOP), member.online() ? member.name() : "",
                        true, false));
            }
            if (!party.viewerIsLeader()) {
                b.line(b.m.get("social.party.not_leader", party.leaderName()));
            } else if (party.full()) {
                b.line(b.m.get("social.party.full", party.members().size(), party.maxSize()));
            } else {
                b.button(b.m.get("social.party.invite.name"), b.m.get("social.party.invite.lore"), Icon.INVITE,
                        SocialMenuAction.of(Kind.PARTY_INVITE));
            }
            b.button(b.m.get("social.party.leave.name"), b.m.get("social.party.leave.lore"), Icon.LEAVE,
                    SocialMenuAction.of(Kind.CONFIRM_LEAVE));
        }
        b.back(SocialMenuAction.of(Kind.HOME));
        return b.m.get("social.party.title");
    }

    private static String partyInvite(SocialView view, Builder b) {
        SocialView.Party party = view.party();
        if (party != null && !party.viewerIsLeader()) {
            b.line(b.m.get("social.party.not_leader", party.leaderName()));
        } else if (party != null && party.full()) {
            b.line(b.m.get("social.party.full", party.members().size(), party.maxSize()));
        } else {
            List<Friend> candidates = inviteCandidates(view);
            b.line(b.m.get(candidates.isEmpty() ? "social.party_invite.empty" : "social.party_invite.content"));
            candidates.stream().limit(MAX_LIST).forEach(friend -> b.add(new Entry(friend.name(),
                    status(friend, b.m), Icon.FRIEND_ONLINE, SocialMenuAction.of(Kind.INVITE, friend.name()),
                    friend.name(), false, false)));
            b.more(candidates.size());
        }
        b.back(SocialMenuAction.of(Kind.PARTY));
        return b.m.get("social.party_invite.title");
    }

    static List<Friend> inviteCandidates(SocialView view) {
        if (!view.canInvite()) return List.of();
        return view.friendsOnServer().stream().filter(friend -> !friend.inMyParty()).toList();
    }

    private static String confirmRemove(String name, Builder b) {
        b.line(b.m.get("social.confirm.remove.content", name));
        b.button(b.m.get("social.confirm.remove.yes", name), "", Icon.REMOVE, SocialMenuAction.of(Kind.REMOVE, name));
        b.footer(b.m.get("social.confirm.cancel"), Icon.BACK, SocialMenuAction.of(Kind.FRIEND, name));
        return b.m.get("social.confirm.remove.title", name);
    }

    private static String confirmLeave(Builder b) {
        b.line(b.m.get("social.confirm.leave.content"));
        b.button(b.m.get("social.confirm.leave.yes"), "", Icon.LEAVE, SocialMenuAction.of(Kind.LEAVE));
        b.footer(b.m.get("social.confirm.cancel"), Icon.BACK, SocialMenuAction.of(Kind.PARTY));
        return b.m.get("social.confirm.leave.title");
    }

    static String status(Friend friend, Messages m) {
        return switch (friend.presence()) {
            case LOBBY -> m.get("social.status.lobby");
            case QUEUED -> m.get("social.status.queued", friend.activity());
            case PLAYING -> m.get("social.status.playing", friend.activity());
            case SPECTATING -> friend.activity().isBlank() ? m.get("social.status.online")
                    : m.get("social.status.spectating", friend.activity());
            case ACTIVITY -> friend.activity().isBlank() ? m.get("social.status.online")
                    : m.get("social.status.activity", friend.activity());
            case ONLINE -> m.get("social.status.online");
            case ELSEWHERE -> m.get("social.status.elsewhere");
            case OFFLINE -> m.get("social.status.offline");
        };
    }

    private static String partySummary(SocialView view, Messages m) {
        if (view.party() == null) {
            return view.partyInvites().isEmpty() ? m.get("social.menu.party.lore_none")
                    : m.get("social.menu.party.lore_invited", view.partyInvites().size());
        }
        return m.get("social.menu.party.lore_members", view.party().members().size(), view.party().maxSize());
    }

    private static final class Builder {
        private final Messages m;
        private final List<String> content = new ArrayList<>();
        private final List<Entry> entries = new ArrayList<>();

        private Builder(Messages m) {
            this.m = m;
        }

        private void line(String text) {
            content.add(text);
        }

        private void add(Entry entry) {
            entries.add(entry);
        }

        private void button(String label, String detail, Icon icon, SocialMenuAction action) {
            entries.add(new Entry(label, detail, icon, action, "", false, false));
        }

        private void friendButton(Friend friend) {
            entries.add(new Entry(friend.name(), status(friend, m),
                    friend.presence().onServer() ? Icon.FRIEND_ONLINE : Icon.FRIEND_OFFLINE,
                    SocialMenuAction.of(Kind.FRIEND, friend.name()),
                    friend.presence().onServer() ? friend.name() : "", false, false));
        }

        private void more(int total) {
            if (total > MAX_LIST) {
                String text = m.get("social.menu.more", total - MAX_LIST);
                entries.add(new Entry(text, "", Icon.MORE, SocialMenuAction.of(Kind.NOOP), "", true, false));
                content.add(text);
            }
        }

        private void back(SocialMenuAction target) {
            footer(m.get("social.menu.back"), Icon.BACK, target);
        }

        private void footer(String label, Icon icon, SocialMenuAction action) {
            entries.add(new Entry(label, "", icon, action, "", false, true));
        }
    }
}
