/*
 * Copyright 2014-2020 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.cluster;

import io.aeron.test.SlowTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.aeron.Aeron.NULL_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.*;

@SlowTest
public class BackupTest
{
    @Test
    @Timeout(30)
    public void shouldBackupClusterNoSnapshotsAndEmptyLog() throws InterruptedException
    {
        try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(NULL_VALUE))
        {
            cluster.awaitLeader();
            cluster.startClusterBackupNode(true);

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);
            cluster.awaitBackupLiveLogPosition(cluster.findLeader().service().cluster().logPosition());

            cluster.stopAllNodes();

            final TestNode node = cluster.startStaticNodeFromBackup();
            cluster.awaitLeader();

            assertEquals(0, node.service().messageCount());
            assertFalse(node.service().wasSnapshotLoaded());
        }
    }

    @Test
    @Timeout(30)
    public void shouldBackupClusterNoSnapshotsAndNonEmptyLog() throws InterruptedException
    {
        try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(NULL_VALUE))
        {
            final TestNode leader = cluster.awaitLeader();

            cluster.connectClient();
            cluster.sendMessages(10);
            cluster.awaitResponseMessageCount(10);
            cluster.awaitServiceMessageCount(leader, 10);

            final long logPosition = leader.service().cluster().logPosition();

            cluster.startClusterBackupNode(true);

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);
            cluster.awaitBackupLiveLogPosition(logPosition);

            cluster.stopAllNodes();

            final TestNode node = cluster.startStaticNodeFromBackup();
            cluster.awaitLeader();

            assertEquals(10, node.service().messageCount());
            assertFalse(node.service().wasSnapshotLoaded());
        }
    }

    @Test
    @Timeout(30)
    public void shouldBackupClusterNoSnapshotsAndThenSendMessages() throws InterruptedException
    {
        try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(NULL_VALUE))
        {
            final TestNode leader = cluster.awaitLeader();
            cluster.startClusterBackupNode(true);

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);

            cluster.connectClient();
            cluster.sendMessages(10);
            cluster.awaitResponseMessageCount(10);
            cluster.awaitServiceMessageCount(leader, 10);

            final long logPosition = leader.service().cluster().logPosition();

            cluster.awaitBackupLiveLogPosition(logPosition);

            cluster.stopAllNodes();

            final TestNode node = cluster.startStaticNodeFromBackup();
            cluster.awaitLeader();

            assertEquals(10, node.service().messageCount());
            assertFalse(node.service().wasSnapshotLoaded());
        }
    }

    @Test
    @Timeout(30)
    public void shouldBackupClusterWithSnapshot() throws InterruptedException
    {
        try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(NULL_VALUE))
        {
            final TestNode leader = cluster.awaitLeader();

            cluster.connectClient();
            cluster.sendMessages(10);
            cluster.awaitResponseMessageCount(10);
            cluster.awaitServiceMessageCount(leader, 10);

            cluster.takeSnapshot(leader);
            cluster.awaitSnapshotCount(cluster.node(0), 1);
            cluster.awaitSnapshotCount(cluster.node(1), 1);
            cluster.awaitSnapshotCount(cluster.node(2), 1);

            final long logPosition = leader.service().cluster().logPosition();

            cluster.startClusterBackupNode(true);

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);
            cluster.awaitBackupLiveLogPosition(logPosition);

            cluster.stopAllNodes();

            final TestNode node = cluster.startStaticNodeFromBackup();
            cluster.awaitLeader();

            assertEquals(10, node.service().messageCount());
            assertTrue(node.service().wasSnapshotLoaded());
        }
    }

    @Test
    @Timeout(30)
    public void shouldBackupClusterWithSnapshotAndNonEmptyLog() throws InterruptedException
    {
        try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(NULL_VALUE))
        {
            final TestNode leader = cluster.awaitLeader();

            cluster.connectClient();
            final int preSnapshotMessageCount = 10;
            final int postSnapshotMessageCount = 7;
            final int totalMessageCount = preSnapshotMessageCount + postSnapshotMessageCount;
            cluster.sendMessages(preSnapshotMessageCount);
            cluster.awaitResponseMessageCount(preSnapshotMessageCount);
            cluster.awaitServiceMessageCount(leader, preSnapshotMessageCount);

            cluster.takeSnapshot(leader);
            cluster.awaitSnapshotCount(cluster.node(0), 1);
            cluster.awaitSnapshotCount(cluster.node(1), 1);
            cluster.awaitSnapshotCount(cluster.node(2), 1);

            cluster.sendMessages(postSnapshotMessageCount);
            cluster.awaitResponseMessageCount(totalMessageCount);
            cluster.awaitServiceMessageCount(leader, totalMessageCount);

            final long logPosition = leader.service().cluster().logPosition();

            cluster.startClusterBackupNode(true);

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);
            cluster.awaitBackupLiveLogPosition(logPosition);

            cluster.stopAllNodes();

            final TestNode node = cluster.startStaticNodeFromBackup();
            cluster.awaitLeader();
            cluster.awaitServiceMessageCount(node, totalMessageCount);

            assertEquals(totalMessageCount, node.service().messageCount());
            assertTrue(node.service().wasSnapshotLoaded());
        }
    }

    @Test
    @Timeout(30)
    public void shouldBackupClusterWithSnapshotThenSend() throws InterruptedException
    {
        try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(NULL_VALUE))
        {
            final TestNode leader = cluster.awaitLeader();

            cluster.connectClient();
            final int preSnapshotMessageCount = 10;
            final int postSnapshotMessageCount = 7;
            final int totalMessageCount = preSnapshotMessageCount + postSnapshotMessageCount;
            cluster.sendMessages(preSnapshotMessageCount);
            cluster.awaitResponseMessageCount(preSnapshotMessageCount);
            cluster.awaitServiceMessageCount(leader, preSnapshotMessageCount);

            cluster.takeSnapshot(leader);
            cluster.awaitSnapshotCount(cluster.node(0), 1);
            cluster.awaitSnapshotCount(cluster.node(1), 1);
            cluster.awaitSnapshotCount(cluster.node(2), 1);

            cluster.startClusterBackupNode(true);

            cluster.sendMessages(postSnapshotMessageCount);
            cluster.awaitResponseMessageCount(totalMessageCount);
            cluster.awaitServiceMessageCount(leader, totalMessageCount);

            final long logPosition = leader.service().cluster().logPosition();

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);
            cluster.awaitBackupLiveLogPosition(logPosition);

            cluster.stopAllNodes();

            final TestNode node = cluster.startStaticNodeFromBackup();
            cluster.awaitLeader();
            cluster.awaitServiceMessageCount(node, totalMessageCount);

            assertEquals(totalMessageCount, node.service().messageCount());
            assertTrue(node.service().wasSnapshotLoaded());
        }
    }

    @Test
    @Timeout(30)
    public void shouldBeAbleToGetTimeOfNextBackupQuery() throws InterruptedException
    {
        try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(NULL_VALUE))
        {
            cluster.awaitLeader();
            final TestBackupNode backupNode = cluster.startClusterBackupNode(true);

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);

            final long nowMs = backupNode.epochClock().time();
            assertThat(backupNode.nextBackupQueryDeadlineMs(), greaterThan(nowMs));
        }
    }

    @Test
    @Timeout(30)
    public void shouldBackupClusterNoSnapshotsAndNonEmptyLogWithReQuery() throws InterruptedException
    {
        try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(NULL_VALUE))
        {
            final TestNode leader = cluster.awaitLeader();

            cluster.connectClient();
            cluster.sendMessages(10);
            cluster.awaitResponseMessageCount(10);
            cluster.awaitServiceMessageCount(leader, 10);

            final long logPosition = leader.service().cluster().logPosition();

            final TestBackupNode backupNode = cluster.startClusterBackupNode(true);

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);
            cluster.awaitBackupLiveLogPosition(logPosition);

            assertTrue(backupNode.nextBackupQueryDeadlineMs(0));

            cluster.sendMessages(5);
            cluster.awaitResponseMessageCount(15);
            cluster.awaitServiceMessageCount(leader, 15);

            final long nextLogPosition = leader.service().cluster().logPosition();
            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);
            cluster.awaitBackupLiveLogPosition(nextLogPosition);

            cluster.stopAllNodes();

            final TestNode node = cluster.startStaticNodeFromBackup();
            cluster.awaitLeader();

            assertEquals(15, node.service().messageCount());
        }
    }

    @Test
    @Timeout(40)
    public void shouldBackupClusterNoSnapshotsAndNonEmptyLogAfterFailure() throws InterruptedException
    {
        try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(NULL_VALUE))
        {
            final TestNode leader = cluster.awaitLeader();

            cluster.connectClient();
            cluster.sendMessages(10);
            cluster.awaitResponseMessageCount(10);
            cluster.awaitServiceMessageCount(leader, 10);

            cluster.stopNode(leader);

            final TestNode nextLeader = cluster.awaitLeader();

            final long logPosition = nextLeader.service().cluster().logPosition();

            cluster.startClusterBackupNode(true);

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);
            cluster.awaitBackupLiveLogPosition(logPosition);

            cluster.stopAllNodes();

            final TestNode node = cluster.startStaticNodeFromBackup();
            cluster.awaitLeader();

            assertEquals(10, node.service().messageCount());
            assertFalse(node.service().wasSnapshotLoaded());
        }
    }

    @Test
    @Timeout(40)
    public void shouldBackupClusterNoSnapshotsAndNonEmptyLogWithFailure() throws InterruptedException
    {
        try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(NULL_VALUE))
        {
            final TestNode leader = cluster.awaitLeader();

            cluster.connectClient();
            cluster.sendMessages(10);
            cluster.awaitResponseMessageCount(10);
            cluster.awaitServiceMessageCount(leader, 10);

            final long logPosition = leader.service().cluster().logPosition();

            cluster.startClusterBackupNode(true);

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);
            cluster.awaitBackupLiveLogPosition(logPosition);

            cluster.stopNode(leader);

            final TestNode nextLeader = cluster.awaitLeader();

            cluster.sendMessages(5);
            cluster.awaitResponseMessageCount(15);
            cluster.awaitServiceMessageCount(nextLeader, 15);

            final long nextLogPosition = nextLeader.service().cluster().logPosition();

            cluster.awaitBackupState(ClusterBackup.State.BACKING_UP);
            cluster.awaitBackupLiveLogPosition(nextLogPosition);

            cluster.stopAllNodes();

            final TestNode node = cluster.startStaticNodeFromBackup();
            cluster.awaitLeader();

            assertEquals(15, node.service().messageCount());
            assertFalse(node.service().wasSnapshotLoaded());
        }
    }
}
