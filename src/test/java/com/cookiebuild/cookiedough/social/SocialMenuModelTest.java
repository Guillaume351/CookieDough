package com.cookiebuild.cookiedough.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.social.SocialMenuAction.Kind;
import com.cookiebuild.cookiedough.social.SocialView.Friend;
import com.cookiebuild.cookiedough.social.SocialView.Member;
import com.cookiebuild.cookiedough.social.SocialView.Party;
import com.cookiebuild.cookiedough.social.SocialView.PartyInvite;
import com.cookiebuild.cookiedough.social.SocialView.Presence;
import com.cookiebuild.cookiedough.utils.LocaleManager;

class SocialMenuModelTest {
    private static final SocialMenuModel.Messages KEYS =
            (key, args) -> args.length == 0 ? key : key + Arrays.toString(args);

    private static SocialView view(boolean bedrock, List<Friend> friends, List<String> incoming,
            List<String> outgoing, Party party, List<PartyInvite> invites, List<String> online) {
        return new SocialView("Me", bedrock, true, "", friends, incoming, outgoing, party, invites, online);
    }

    private static List<SocialMenuAction> actions(SocialPage page) {
        return page.entries().stream().map(SocialPage.Entry::action).toList();
    }

    @Test
    void homeSurfacesInvitesRequestsAndOnlineFriendsBeforeNavigation() {
        UUID leader = UUID.randomUUID();
        SocialView view = view(true,
                List.of(new Friend("Alex", Presence.LOBBY, "", false),
                        new Friend("Sam", Presence.OFFLINE, "", false)),
                List.of("Nina"), List.of(), null, List.of(new PartyInvite(leader, "Lea")), List.of());

        SocialPage page = SocialMenuModel.build(SocialPageId.HOME, "", view, null, KEYS);

        assertEquals(List.of(
                SocialMenuAction.of(Kind.JOIN_PARTY, leader.toString()),
                SocialMenuAction.of(Kind.REQUESTS),
                SocialMenuAction.of(Kind.FRIEND, "Alex"),
                SocialMenuAction.of(Kind.ADD),
                SocialMenuAction.of(Kind.PARTY),
                SocialMenuAction.of(Kind.FRIENDS),
                SocialMenuAction.of(Kind.CLOSE)), actions(page));
        assertEquals("social.menu.summary[1, 1]", page.content().getFirst());
    }

    @Test
    void homeHidesPartyInvitesWhileAlreadyInAParty() {
        Party party = new Party("Me", true, List.of(new Member("Me", true, true)), 4);
        SocialView view = view(false, List.of(), List.of(), List.of(), party,
                List.of(new PartyInvite(UUID.randomUUID(), "Lea")), List.of());

        SocialPage page = SocialMenuModel.build(SocialPageId.HOME, "", view, null, KEYS);

        assertTrue(actions(page).stream().noneMatch(action -> action.kind() == Kind.JOIN_PARTY));
        assertTrue(page.content().contains("social.menu.no_online_friends"));
    }

    @Test
    void friendDetailOffersInviteFollowAndRemoveForQueuedFriend() {
        SocialView view = view(false, List.of(new Friend("Alex", Presence.QUEUED, "BedWars", false)),
                List.of(), List.of(), null, List.of(), List.of("Alex"));

        SocialPage page = SocialMenuModel.build(SocialPageId.FRIEND, "Alex", view, null, KEYS);

        assertEquals(List.of(
                SocialMenuAction.of(Kind.INVITE, "Alex"),
                SocialMenuAction.of(Kind.JOIN_QUEUE, "Alex"),
                SocialMenuAction.of(Kind.CONFIRM_REMOVE, "Alex"),
                SocialMenuAction.of(Kind.HOME)), actions(page));
        assertEquals("social.status.queued[BedWars]", page.content().getFirst());
    }

    @Test
    void followIsHiddenWhenNothingToJoinOrViewerCannotQueueOrIsAlreadyThere() {
        Friend lobby = new Friend("Alex", Presence.LOBBY, "", false);
        Friend playing = new Friend("Alex", Presence.PLAYING, "SkyWars", false);
        Friend elsewhere = new Friend("Alex", Presence.ELSEWHERE, "", false);
        assertFalse(SocialMenuModel.canFollow(view(false, List.of(lobby), List.of(), List.of(), null,
                List.of(), List.of()), lobby));
        assertTrue(SocialMenuModel.canFollow(view(false, List.of(playing), List.of(), List.of(), null,
                List.of(), List.of()), playing));
        assertFalse(SocialMenuModel.canFollow(new SocialView("Me", false, false, "", List.of(playing), List.of(),
                List.of(), null, List.of(), List.of()), playing));
        assertFalse(SocialMenuModel.canFollow(new SocialView("Me", false, true, "skywars", List.of(playing),
                List.of(), List.of(), null, List.of(), List.of()), playing));
        assertFalse(SocialMenuModel.canFollow(view(false, List.of(elsewhere), List.of(), List.of(), null,
                List.of(), List.of()), elsewhere));
    }

    @Test
    void partyMemberWhoIsNotLeaderCannotInviteFromFriendDetail() {
        Party party = new Party("Lea", false, List.of(new Member("Lea", true, true), new Member("Me", true, false)), 4);
        SocialView view = view(false, List.of(new Friend("Alex", Presence.LOBBY, "", false)), List.of(), List.of(),
                party, List.of(), List.of());

        SocialPage page = SocialMenuModel.build(SocialPageId.FRIEND, "Alex", view, null, KEYS);

        assertTrue(actions(page).stream().noneMatch(action -> action.kind() == Kind.INVITE));
        assertTrue(SocialMenuModel.inviteCandidates(view).isEmpty());
    }

    @Test
    void addListExcludesSelfFriendsAndSentRequestsAndPutsIncomingFirst() {
        SocialView view = view(true, List.of(new Friend("Alex", Presence.LOBBY, "", false)), List.of("zed"),
                List.of("Bob"), null, List.of(), List.of("me", "alex", "Bob", "Carla", "Zed", "Carla"));

        assertEquals(List.of("Zed", "Carla"), SocialMenuModel.addCandidates(view));
        SocialPage page = SocialMenuModel.build(SocialPageId.ADD, "", view, null, KEYS);
        assertEquals(List.of(
                SocialMenuAction.of(Kind.SEND_REQUEST, "Zed"),
                SocialMenuAction.of(Kind.SEND_REQUEST, "Carla"),
                SocialMenuAction.of(Kind.ADD_BY_NAME),
                SocialMenuAction.of(Kind.HOME)), actions(page));
    }

    @Test
    void javaAddPageHasNoTextInputButton() {
        SocialView view = view(false, List.of(), List.of(), List.of(), null, List.of(), List.of("Carla"));

        SocialPage page = SocialMenuModel.build(SocialPageId.ADD, "", view, null, KEYS);

        assertTrue(actions(page).stream().noneMatch(action -> action.kind() == Kind.ADD_BY_NAME));
    }

    @Test
    void partyPageShowsMembersToJavaOnlyAndOffersInviteAndLeaveToLeader() {
        Party party = new Party("Me", true, List.of(new Member("Me", true, true), new Member("Alex", false, false)), 4);
        SocialView view = view(true, List.of(), List.of(), List.of(), party, List.of(), List.of());

        SocialPage page = SocialMenuModel.build(SocialPageId.PARTY, "", view, null, KEYS);

        assertEquals(2, page.entries().stream().filter(SocialPage.Entry::decorative).count());
        assertEquals(List.of(
                SocialMenuAction.of(Kind.PARTY_INVITE),
                SocialMenuAction.of(Kind.CONFIRM_LEAVE),
                SocialMenuAction.of(Kind.HOME)),
                page.bedrockButtons().stream().map(SocialPage.Entry::action).toList());
    }

    @Test
    void fullPartyCannotInviteAnyone() {
        List<Member> members = List.of(new Member("Me", true, true), new Member("A", true, false),
                new Member("B", true, false), new Member("C", true, false));
        SocialView view = view(false, List.of(new Friend("D", Presence.LOBBY, "", false)), List.of(), List.of(),
                new Party("Me", true, members, 4), List.of(), List.of());

        SocialPage invite = SocialMenuModel.build(SocialPageId.PARTY_INVITE, "", view, null, KEYS);

        assertEquals(List.of(SocialMenuAction.of(Kind.PARTY)), actions(invite));
        assertTrue(invite.content().contains("social.party.full[4, 4]"));
    }

    @Test
    void partyInvitePickerListsOnlyFriendsOnThisServerOutsideTheParty() {
        SocialView view = view(false, List.of(
                new Friend("Alex", Presence.PLAYING, "SkyWars", false),
                new Friend("Bea", Presence.LOBBY, "", true),
                new Friend("Cy", Presence.ELSEWHERE, "", false),
                new Friend("Dee", Presence.OFFLINE, "", false)), List.of(), List.of(), null, List.of(), List.of());

        assertEquals(List.of("Alex"), SocialMenuModel.inviteCandidates(view).stream().map(Friend::name).toList());
    }

    @Test
    void longListsAreCappedToFitOneChestWithAMoreHint() {
        List<Friend> friends = new ArrayList<>();
        for (int index = 0; index < 50; index++) friends.add(new Friend("P" + index, Presence.OFFLINE, "", false));
        SocialView view = view(false, friends, List.of(), List.of(), null, List.of(), List.of());

        SocialPage page = SocialMenuModel.build(SocialPageId.FRIENDS, "", view, null, KEYS);

        assertEquals(SocialMenuModel.MAX_LIST, page.entries().stream()
                .filter(entry -> entry.action().kind() == Kind.FRIEND).count());
        assertTrue(page.content().contains("social.menu.more[14]"));
        assertTrue(page.entries().size() <= 45 + 4);
    }

    @Test
    void requestPageAnswersOnlyAPendingRequestAndNoticeComesFirst() {
        SocialView view = view(false, List.of(), List.of("Nina"), List.of(), null, List.of(), List.of());

        SocialPage page = SocialMenuModel.build(SocialPageId.REQUEST, "Nina", view, "Done!", KEYS);
        SocialPage stale = SocialMenuModel.build(SocialPageId.REQUEST, "Bob", view, null, KEYS);

        assertEquals("Done!", page.content().getFirst());
        assertEquals(List.of(SocialMenuAction.of(Kind.ACCEPT, "Nina"), SocialMenuAction.of(Kind.DENY, "Nina"),
                SocialMenuAction.of(Kind.REQUESTS)), actions(page));
        assertEquals(List.of(SocialMenuAction.of(Kind.REQUESTS)), actions(stale));
    }

    @Test
    void everyPageRendersWithRealFrenchMessages() {
        UUID leader = UUID.randomUUID();
        SocialView view = view(true, List.of(new Friend("Alex", Presence.QUEUED, "BedWars", false)),
                List.of("Nina"), List.of("Bob"), null, List.of(new PartyInvite(leader, "Lea")), List.of("Carla"));
        SocialMenuModel.Messages french = (key, args) -> LocaleManager.getMessage(key, Locale.FRENCH, args);
        for (SocialPageId id : SocialPageId.values()) {
            String context = id == SocialPageId.REQUEST ? "Nina" : "Alex";
            SocialPage page = SocialMenuModel.build(id, context, view, null, french);
            for (SocialPage.Entry entry : page.entries()) {
                assertFalse(entry.label().startsWith("social."), id + " label " + entry.label());
                assertFalse(entry.detail().startsWith("social."), id + " detail " + entry.detail());
            }
            page.content().forEach(line -> assertFalse(line.startsWith("social."), id + " content " + line));
        }
        assertEquals("Amis & groupe", SocialMenuModel.build(SocialPageId.HOME, "", view, null, french).title());
    }
}
