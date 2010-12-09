/*
Copyright (c) 2007-2010, Yusuke Yamamoto
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the Yusuke Yamamoto nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Yusuke Yamamoto ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Yusuke Yamamoto BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package twitter4j;

import twitter4j.json.DataObjectFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Yusuke Yamamoto - yusuke at mac.com
 * @since Twitter4J 2.1.9
 */
public class UserStreamTest extends TwitterTestBase implements UserStreamListener {
    public UserStreamTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private User source;
    private User target;

    Exception ex;

    public void testUserStreamEventTypes() throws Exception {
        InputStream is = TwitterTestBase.class.getResourceAsStream("/streamingapi-event-testcase.json");
        UserStream stream = new UserStreamImpl(is);

        source = null;
        target = null;
        ex = null;

        stream.next(this);
        assertEquals(23456789, source.getId());
        assertEquals(12345678, target.getId());
        assertNull(ex);

        source = null;
        target = null;
        ex = null;

        // This one is an unknown event type.  We should safely ignore it.
        stream.next(this);
        assertNull(source);
        assertNull(target);
        assertNull(ex);
    }

    public void testUserStream() throws Exception {
        TwitterStream twitterStream = new TwitterStreamFactory(conf1).getInstance();
        twitterStream.addListener(this);
        try {
            twitter1.destroyBlock(6377362);
        } catch (TwitterException ignore) {
        }
        try {
            twitter2.destroyBlock(6358482);
        } catch (TwitterException ignore) {
        }
        try {
            twitter1.createFriendship(6377362);
        } catch (TwitterException ignore) {
        }
        try {
            twitter2.createFriendship(6358482);
        } catch (TwitterException ignore) {
        }

        //twit4j: 6358482
        //twit4j2: 6377362
        twitterStream.user(new String[]{"BAh7CToPY3JlYXR"});
        //expecting onFriendList for twit4j and twit4j2
        waitForStatus();
        waitForStatus();

        Status status = twitter2.updateStatus("@twit4j " + new Date());
        //expecting onStatus for twit4j from twit4j
        waitForStatus();

        twitter1.createFavorite(status.getId());
        waitForStatus();

        twitter1.destroyFavorite(status.getId());
        waitForStatus();

        // unfollow twit4j
        twitter2.destroyFriendship(6358482);
        waitForStatus();

        // follow twit4j
        twitter2.createFriendship(6358482);
        waitForStatus();

        // unfollow twit4j2
        twitter1.destroyFriendship(6377362);
        waitForStatus();

        status = twitter2.updateStatus("somerandometext " + new Date());
        waitForStatus();
        // follow twit4j2
        twitter1.createFriendship(6377362);
        waitForStatus();

        twitter1.retweetStatus(status.getId());
        waitForStatus();
        DirectMessage dm = twitter1.sendDirectMessage(6377362, "test " + new Date());
        waitForStatus();

        twitter2.destroyStatus(status.getId());
        waitForStatus();

        twitter1.destroyDirectMessage(dm.getId());
        waitForStatus();

        // block twit4j
        twitter1.createBlock(6377362);
        waitForStatus();

        // unblock twit4j
        twitter1.destroyBlock(6377362);
        waitForStatus();

        try {
            twitter1.createFriendship(6377362);
        } catch (TwitterException ignore) {
        }
        try {
            twitter2.createFriendship(6358482);
        } catch (TwitterException ignore) {
        }

        UserList list = twitter1.createUserList("test", true, "desctription");
        waitForStatus();
        list = twitter1.updateUserList(list.getId(), "test2", true, "description2");
        waitForStatus();
        twitter1.addUserListMember(list.getId(), 6377362);
        twitter2.subscribeUserList("twit4j", list.getId());
        waitForStatus();
        twitter2.unsubscribeUserList("twit4j", list.getId());
        waitForStatus();
        twitter1.destroyUserList(list.getId());
        waitForStatus();

        // confirm if tracking term is effective
        boolean found = false;
        for (Object[] event : this.received) {
            if ("onstatus".equals(event[0])) {
                Status status1 = (Status)event[1];
                if(-1 != status1.getText().indexOf("somerandometext"));
                found = true;
                break;
            }
        }
        assertTrue(found);

        assertReceived("onstatus");
        assertReceived("onfriendlist");
        assertReceived(TwitterMethod.CREATE_FAVORITE);
        assertReceived(TwitterMethod.DESTROY_FAVORITE);
        // http://groups.google.com/group/twitter-development-talk/browse_thread/thread/d29c5bd56c49c23c#
//        assertReceived(TwitterMethod.DESTROY_FRIENDSHIP);
        assertReceived(TwitterMethod.CREATE_FRIENDSHIP);
        // http://groups.google.com/group/twitter-development-talk/browse_thread/thread/d29c5bd56c49c23c#
//        assertReceived(TwitterMethod.RETWEET_STATUS);
        assertReceived(TwitterMethod.SEND_DIRECT_MESSAGE);
        assertReceived(TwitterMethod.DESTROY_STATUS);
        assertReceived(TwitterMethod.DESTROY_DIRECT_MESSAGE);

        assertReceived(TwitterMethod.SUBSCRIBE_LIST);
        assertReceived(TwitterMethod.CREATE_USER_LIST);
        assertReceived(TwitterMethod.UPDATE_USER_LIST);
        assertReceived(TwitterMethod.DESTROY_USER_LIST);


        assertReceived(TwitterMethod.CREATE_BLOCK);
        assertReceived(TwitterMethod.DESTROY_BLOCK);
    }

    private void assertReceived(Object obj) {
        boolean received = false;
        for (Object[] event : this.received) {
            if (obj.equals(event[0])) {
                received = true;
                break;
            }
        }
        assertTrue(received);
    }

    private synchronized void waitForStatus() {
        try {
            this.wait(5000);
            System.out.println("notified.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    List<Object[]> received = new ArrayList<Object[]>(3);

    private synchronized void notifyResponse() {
        this.notify();
    }

    public void onStatus(Status status) {
        received.add(new Object[]{"onstatus", status});
        assertNotNull(DataObjectFactory.getRawJSON(status));
        notifyResponse();
    }
    public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice){
        received.add(new Object[]{TwitterMethod.DESTROY_STATUS, statusDeletionNotice});
        notifyResponse();
    }
    public void onDeletionNotice(int directMessageId, int userId){
        received.add(new Object[]{TwitterMethod.DESTROY_DIRECT_MESSAGE, userId});
        notifyResponse();
    }
    public void onTrackLimitationNotice(int numberOfLimitedStatuses){
        received.add(new Object[]{"tracklimitation", numberOfLimitedStatuses});
        notifyResponse();
    }

    public void onFriendList(int[] friendIds) {
        received.add(new Object[]{"onfriendlist", friendIds});
        notifyResponse();
    }

    public void onFavorite(User source, User target, Status favoritedStatus) {
        received.add(new Object[]{TwitterMethod.CREATE_FAVORITE, source, target, favoritedStatus});
        assertNotNull(DataObjectFactory.getRawJSON(source));
        assertNotNull(DataObjectFactory.getRawJSON(target));
        assertNotNull(DataObjectFactory.getRawJSON(favoritedStatus));
        notifyResponse();
    }

    public void onUnfavorite(User source, User target, Status unfavoritedStatus) {
        received.add(new Object[]{TwitterMethod.DESTROY_FAVORITE, source, target, unfavoritedStatus});
        assertNotNull(DataObjectFactory.getRawJSON(source));
        assertNotNull(DataObjectFactory.getRawJSON(target));
        assertNotNull(DataObjectFactory.getRawJSON(unfavoritedStatus));
        notifyResponse();
    }

    public void onFollow(User source, User followedUser) {
        received.add(new Object[]{TwitterMethod.CREATE_FRIENDSHIP, source, followedUser});
        assertNotNull(DataObjectFactory.getRawJSON(source));
        assertNotNull(DataObjectFactory.getRawJSON(followedUser));
        notifyResponse();
    }

    public void onUnfollow(User source, User unfollowedUser) {
        received.add(new Object[]{TwitterMethod.DESTROY_FRIENDSHIP, source, unfollowedUser});
        this.source = source;
        this.target = unfollowedUser;
        assertNotNull(DataObjectFactory.getRawJSON(source));
        assertNotNull(DataObjectFactory.getRawJSON(unfollowedUser));
        notifyResponse();
    }

    public void onRetweet(User source, User target, Status retweetedStatus) {
        received.add(new Object[]{TwitterMethod.RETWEET_STATUS, retweetedStatus});
        assertNotNull(DataObjectFactory.getRawJSON(retweetedStatus));
        notifyResponse();
    }

    public void onDirectMessage(DirectMessage directMessage) {
        received.add(new Object[]{TwitterMethod.SEND_DIRECT_MESSAGE, directMessage});
        assertNotNull(DataObjectFactory.getRawJSON(directMessage));
        notifyResponse();
    }

    public void onUserListSubscribed(User subscriber, User listOwner, UserList list) {
        received.add(new Object[]{TwitterMethod.SUBSCRIBE_LIST, subscriber, listOwner, list});
        assertNotNull(DataObjectFactory.getRawJSON(subscriber));
        assertNotNull(DataObjectFactory.getRawJSON(listOwner));
        assertNotNull(DataObjectFactory.getRawJSON(list));
        notifyResponse();
    }

    public void onUserListCreated(User listOwner, UserList list) {
        received.add(new Object[]{TwitterMethod.CREATE_USER_LIST, listOwner, list});
        assertNotNull(DataObjectFactory.getRawJSON(listOwner));
        assertNotNull(DataObjectFactory.getRawJSON(list));
        notifyResponse();
    }

    public void onUserListUpdated(User listOwner, UserList list) {
        received.add(new Object[]{TwitterMethod.UPDATE_USER_LIST, listOwner, list});
        assertNotNull(DataObjectFactory.getRawJSON(listOwner));
        assertNotNull(DataObjectFactory.getRawJSON(list));
        notifyResponse();
    }

    public void onUserListDestroyed(User listOwner, UserList list) {
        received.add(new Object[]{TwitterMethod.DESTROY_USER_LIST, listOwner, list});
        notifyResponse();
        assertNotNull(DataObjectFactory.getRawJSON(listOwner));
        assertNotNull(DataObjectFactory.getRawJSON(list));
    }

    public void onBlock(User source, User blockedUser) {
        received.add(new Object[]{TwitterMethod.CREATE_BLOCK, source, blockedUser});
        assertNotNull(DataObjectFactory.getRawJSON(source));
        assertNotNull(DataObjectFactory.getRawJSON(blockedUser));
        notifyResponse();
    }

    public void onUnblock(User source, User unblockedUser) {
        received.add(new Object[]{TwitterMethod.DESTROY_BLOCK, source, unblockedUser});
        assertNotNull(DataObjectFactory.getRawJSON(source));
        assertNotNull(DataObjectFactory.getRawJSON(unblockedUser));
        notifyResponse();
    }

    public void onException(Exception ex) {
        received.add(new Object[]{ex});
        notifyResponse();
    }


}
