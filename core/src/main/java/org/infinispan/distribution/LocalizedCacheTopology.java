package org.infinispan.distribution;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.infinispan.commons.hash.MurmurHash3;
import org.infinispan.commons.util.ImmutableHopscotchHashSet;
import org.infinispan.commons.util.ImmutableIntSet;
import org.infinispan.commons.util.Immutables;
import org.infinispan.commons.util.IntSet;
import org.infinispan.commons.util.RangeSet;
import org.infinispan.commons.util.SmallIntSet;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.distribution.ch.KeyPartitioner;
import org.infinispan.distribution.ch.impl.ReplicatedConsistentHash;
import org.infinispan.remoting.transport.Address;
import org.infinispan.topology.CacheTopology;

/**
 * Extends {@link CacheTopology} with information about keys owned by the local node.
 *
 * @author Dan Berindei
 * @since 9.0
 */
public class LocalizedCacheTopology extends CacheTopology {

   private final Address localAddress;
   private final Set<Address> membersSet;
   private final KeyPartitioner keyPartitioner;
   private final boolean isDistributed;
   private final boolean allLocal;
   private final boolean isSegmented;
   private final int numSegments;
   private final int maxOwners;
   private final DistributionInfo[] distributionInfos;
   private final boolean isScattered;
   private final IntSet localReadSegments;

   public static LocalizedCacheTopology makeSingletonTopology(CacheMode cacheMode, Address localAddress) {
      return makeSingletonTopology(cacheMode, k -> 0, 1, localAddress);
   }

   public static LocalizedCacheTopology makeSingletonTopology(CacheMode cacheMode, KeyPartitioner keyPartitioner,
         int numSegments, Address localAddress) {
      List<Address> members = Collections.singletonList(localAddress);
      int[] owners = new int[numSegments];
      Arrays.fill(owners, 0);
      ConsistentHash ch = new ReplicatedConsistentHash(MurmurHash3.getInstance(), members, owners);
      CacheTopology cacheTopology = new CacheTopology(0, 0, ch, null, Phase.NO_REBALANCE, members, null);
      return new LocalizedCacheTopology(cacheMode, cacheTopology, keyPartitioner, localAddress);
   }

   public LocalizedCacheTopology(CacheMode cacheMode, CacheTopology cacheTopology, KeyPartitioner keyPartitioner,
                                 Address localAddress) {
      super(cacheTopology.getTopologyId(), cacheTopology.getRebalanceId(), cacheTopology.getCurrentCH(),
            cacheTopology.getPendingCH(), cacheTopology.getUnionCH(), cacheTopology.getPhase(), cacheTopology.getActualMembers(),
            cacheTopology.getMembersPersistentUUIDs());

      ConsistentHash readCH = getReadConsistentHash();
      ConsistentHash writeCH = getWriteConsistentHash();

      this.localAddress = localAddress;
      this.membersSet = new ImmutableHopscotchHashSet<>(cacheTopology.getMembers());
      this.keyPartitioner = keyPartitioner;
      this.isDistributed = cacheMode.isDistributed();
      isScattered = cacheMode.isScattered();
      boolean isReplicated = cacheMode.isReplicated();
      this.isSegmented = isDistributed || isReplicated || isScattered;
      this.numSegments = readCH.getNumSegments();

      if (isDistributed || isScattered) {
         this.distributionInfos = new DistributionInfo[numSegments];
         int maxOwners = 1;
         IntSet localReadSegments = new SmallIntSet(numSegments);
         for (int segmentId = 0; segmentId < numSegments; segmentId++) {
            Address primary = readCH.locatePrimaryOwnerForSegment(segmentId);
            List<Address> readOwners = readCH.locateOwnersForSegment(segmentId);
            List<Address> writeOwners = writeCH.locateOwnersForSegment(segmentId);
            Collection<Address> writeBackups = isScattered ? Collections.emptyList() : writeOwners.subList(1, writeOwners.size());
            this.distributionInfos[segmentId] =
                  new DistributionInfo(segmentId, primary, readOwners, writeOwners, writeBackups, localAddress);
            maxOwners = Math.max(maxOwners, writeOwners.size());
            if (readOwners.contains(localAddress)) {
               localReadSegments.set(segmentId);
            }
         }
         this.maxOwners = maxOwners;
         this.allLocal = false;
         this.localReadSegments = new ImmutableIntSet(localReadSegments);
      } else if (isReplicated) {
         // Writes must be broadcast to the entire cluster
         Map<Address, List<Address>> readOwnersMap = new HashMap<>();
         Map<Address, List<Address>> writeOwnersMap = new HashMap<>();
         this.distributionInfos = new DistributionInfo[numSegments];
         for (int segmentId = 0; segmentId < numSegments; segmentId++) {
            int segmentCopy = segmentId;
            Address primary = readCH.locatePrimaryOwnerForSegment(segmentId);
            List<Address> readOwners = readOwnersMap.computeIfAbsent(primary, p ->
                  Immutables.immutableListCopy(readCH.locateOwnersForSegment(segmentCopy)));
            List<Address> writeOwners = writeOwnersMap.computeIfAbsent(primary, p ->
                  Immutables.immutableListCopy(writeCH.locateOwnersForSegment(segmentCopy)));
            List<Address> writeBackups = writeOwners.subList(1, writeOwners.size());
            this.distributionInfos[segmentId] =
                  new DistributionInfo(segmentId, primary, readOwners, writeOwners, writeBackups, localAddress);
         }
         this.maxOwners = cacheTopology.getMembers().size();
         this.allLocal = readOwnersMap.containsKey(localAddress);
         this.localReadSegments = new RangeSet(allLocal ? numSegments : 0);
      } else { // Invalidation/Local
         assert cacheMode.isInvalidation() || cacheMode == CacheMode.LOCAL;
         // Reads and writes are local, only the invalidation is replicated
         List<Address> owners = Collections.singletonList(localAddress);
         List<Address> writeBackups = Collections.emptyList();
         this.distributionInfos = new DistributionInfo[]{
               new DistributionInfo(0, localAddress, owners, owners, writeBackups, localAddress)
         };
         this.maxOwners = 1;
         this.allLocal = true;
         this.localReadSegments = new RangeSet(numSegments);
      }
   }

   /**
    * @return {@code true} iff key {@code key} can be read without going remote.
    */
   public boolean isReadOwner(Object key) {
      if (allLocal)
         return true;

      int segmentId = keyPartitioner.getSegment(key);
      return distributionInfos[segmentId].isReadOwner();
   }


   /**
    * @return {@code true} iff writing a value for key {@code key} will update it on the local node.
    */
   public boolean isWriteOwner(Object key) {
      if (allLocal)
         return true;

      int segmentId = keyPartitioner.getSegment(key);
      return distributionInfos[segmentId].isWriteOwner();
   }

   /**
    * @return The consistent hash segment of key {@code key}
    */
   public int getSegment(Object key) {
      return keyPartitioner.getSegment(key);
   }

   /**
    * @return Information about the ownership of segment {@code segment}, including the primary owner.
    */
   public DistributionInfo getDistributionForSegment(int segmentId) {
      return distributionInfos[segmentId];
   }

   /**
    * @return Information about the ownership of key {@code key}, including the primary owner.
    */
   public DistributionInfo getDistribution(Object key) {
      int segmentId = isSegmented ? keyPartitioner.getSegment(key) : 0;
      return distributionInfos[segmentId];
   }

   /**
    * @return An unordered collection with the write owners of {@code key}.
    */
   public Collection<Address> getWriteOwners(Object key) {
      int segmentId = isDistributed || isScattered ? keyPartitioner.getSegment(key) : 0;
      return distributionInfos[segmentId].writeOwners();
   }

   /**
    * @return An unordered collection with the write owners of {@code keys}.
    */
   public Collection<Address> getWriteOwners(Collection<?> keys) {
      if (keys.isEmpty()) {
         return Collections.emptySet();
      }
      if (isDistributed || isScattered) {
         if (keys.size() == 1) {
            Object singleKey = keys.iterator().next();
            return getDistribution(singleKey).writeOwners();
         } else {
            SmallIntSet segments = new SmallIntSet(numSegments);
            // Expecting some overlap between keys
            Set<Address> owners = new HashSet<>(2 * maxOwners);
            for (Object key : keys) {
               int segment = keyPartitioner.getSegment(key);
               if (segments.add(segment)) {
                  owners.addAll(getDistributionForSegment(segment).writeOwners());
               }
            }
            return owners;
         }
      } else {
         return getDistributionForSegment(0).writeOwners();
      }
   }

   /**
    * @return The segments owned by the local node for reading.
    */
   public IntSet getLocalReadSegments() {
      return localReadSegments;
   }

   /**
    * @return The address of the local node.
    */
   public Address getLocalAddress() {
      return localAddress;
   }

   public Set<Address> getMembersSet() {
      return membersSet;
   }
}
