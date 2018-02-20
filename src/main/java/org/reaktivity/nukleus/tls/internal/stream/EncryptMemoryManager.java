/**
 * Copyright 2016-2017 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.reaktivity.nukleus.tls.internal.stream;

import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.reaktivity.nukleus.buffer.DirectBufferBuilder;
import org.reaktivity.nukleus.buffer.MemoryManager;
import org.reaktivity.nukleus.tls.internal.types.ListFW;
import org.reaktivity.nukleus.tls.internal.types.ListFW.Builder;
import org.reaktivity.nukleus.tls.internal.types.stream.RegionFW;

public class EncryptMemoryManager
{
    public static final ListFW<RegionFW> EMPTY_REGION;
    static
    {
        ListFW.Builder<RegionFW.Builder, RegionFW> regionsRW = new Builder<RegionFW.Builder, RegionFW>(
                new RegionFW.Builder(),
                new RegionFW());
        EMPTY_REGION = regionsRW.wrap(new UnsafeBuffer(new byte[100]), 0, 100).build();
    }

    private static final byte EMPTY_REGION_TAG = 0x00;
    private static final byte FULL_REGION_TAG = 0x01;
    private static final byte WRAP_AROUND_REGION_TAG = 0x02;

    private static final int TAG_SIZE_PER_CHUNK = 1;
    private static final int TAG_SIZE_PER_WRITE = TAG_SIZE_PER_CHUNK * 2; // at most generates 2 regions
    private static final int MAX_REGION_SIZE = 1000;

    private final DirectBufferBuilder directBufferBuilderRO;
    private final MemoryManager memoryManager;
    private final MutableDirectBuffer directBufferRW;
    private final ListFW<RegionFW> regionsRO;
    private final LongSupplier writeFramesAccumulator;
    private final LongConsumer writeBytesAccumulator;
    private final long streamId;
    private final int transferCapacity;
    private final long memoryAddress;
    private final long resolvedAddress;
    private final int indexMask;

    private long writeIndex;
    private long ackIndex;

    private final int backlogCapacity = 1024; // TODO: Configuration
    private long backlogAddress;
    private final MutableDirectBuffer backlogRW = new UnsafeBuffer(new byte[0]);
    private final ListFW.Builder<RegionFW.Builder, RegionFW> regionsRW =
            new ListFW.Builder<>(new RegionFW.Builder(), new RegionFW());
    private final DirectBuffer view = new UnsafeBuffer(new byte[0]);

    EncryptMemoryManager(
        MemoryManager memoryManager,
        DirectBufferBuilder directBufferBuilderRO,
        MutableDirectBuffer directBufferRW,
        ListFW<RegionFW> regionsRO,
        int transferCapacity,
        long streamId,
        LongSupplier writeFramesAccumulator,
        LongConsumer writeBytesAccumulator)
    {
        this.directBufferBuilderRO = directBufferBuilderRO;
        this.memoryManager = memoryManager;
        this.directBufferRW = directBufferRW;
        this.regionsRO = regionsRO;

        this.transferCapacity = transferCapacity;
        this.memoryAddress = memoryManager.acquire(transferCapacity);
        this.resolvedAddress = memoryManager.resolve(memoryAddress);
        if (this.memoryAddress == -1)
        {
            throw new IllegalStateException("Unable to allocate memory block: " + transferCapacity);
        }
        this.streamId = streamId;
        this.writeIndex = 0;
        this.ackIndex = 0;

        this.indexMask = transferCapacity - 1;

        this.writeBytesAccumulator = writeBytesAccumulator;
        this.writeFramesAccumulator = writeFramesAccumulator;
        this.backlogAddress = -1;
        Function<Long, Integer> what = this::resolvePartialWrite;
    }

    private long paritalWriteAddress = -1;
    private int partialWritePosition = -1;
    private final MutableInteger sentRegions;
    private final MutableInteger iterCount;

    private int resolvePartialWrite(long addr)
    {
        return addr == paritalWriteAddress ? partialWritePosition : 0;
    }

    public ListFW<RegionFW> stageBacklog(
        ListFW<RegionFW> newRegions,
        ByteBuffer tlsInBuffer)
    {
        tlsInBuffer.clear();
        if (backlogAddress != -1)
        {
            newRegions = appendBacklogRegions(backlogAddress, newRegions);
        }
        iterCount.value = 0;
        newRegions.forEach(r -> // TODO: remove multi line lambda
        {
            iterCount.value++;
            if (iterCount.value > sentRegions.value)
            {
                int partialWrite = resolvePartialWrite(r.address());
                final int length = r.length() - partialWrite;
                view.wrap(r.address() + partialWrite, length);
                view.getBytes(0, tlsInBuffer, tlsInBuffer.position(), length);
                tlsInBuffer.position(tlsInBuffer.position() + length);
            }
        });
        tlsInBuffer.flip();
        return newRegions;
    }

    // Returns the payload size you can accept
    public int maxWriteCapacity(
        ListFW<RegionFW> regions)
    {
        final int metaDataReserve =  regions.sizeof() + TAG_SIZE_PER_WRITE;
        final int unAcked = (int) (writeIndex - ackIndex);
        return transferCapacity - (unAcked + metaDataReserve);
    }

    private ListFW<RegionFW> appendBacklogRegions(
        long address,
        ListFW<RegionFW> regions)
    {
        MutableDirectBuffer backlog = backlogRW;
        backlog.wrap(memoryManager.resolve(backlogAddress), backlogCapacity);
        regionsRW.wrap(backlog, 0, backlog.capacity());
        regionsRO.wrap(backlog, 0, backlog.capacity())
                 .forEach(this::appendRegion);
        regions.forEach(this::appendRegion);
        regions = regionsRW.build();

        return regions;
    }

    private void appendRegion(
        RegionFW region)
    {
        regionsRW.item(r -> r.address(region.address())
                             .length(region.length())
                             .streamId(region.streamId()));
    }

    // know we have room for meta data cause must call maxPayloadSize
    public void packRegions(
        ByteBuffer src,
        int srcIndex,
        int length,
        ListFW<RegionFW> consumedRegions,
        ListFW.Builder<RegionFW.Builder, RegionFW> regionBuilders)
    {
        writeFramesAccumulator.getAsLong();
        writeBytesAccumulator.accept(length);
        final int sizeOfRegions = consumedRegions.isEmpty() ? TAG_SIZE_PER_CHUNK : consumedRegions.sizeof();
        int ackIndex = (int) (indexMask & writeIndex);
        final int rIndex = (int) (indexMask & ackIndex);

        final int lengthToWrap = ((ackIndex >= rIndex ? transferCapacity - ackIndex: rIndex - ackIndex));

        final int bytesToWrite = Math.min(lengthToWrap - TAG_SIZE_PER_CHUNK, length);
        directBufferRW.wrap(resolvedAddress + ackIndex, bytesToWrite);
        directBufferRW.putBytes(0, src, srcIndex, bytesToWrite);

        final long regionAddress = memoryAddress + ackIndex;
        regionBuilders.item(rb -> rb.address(regionAddress).length(bytesToWrite).streamId(streamId));
        ackIndex += bytesToWrite;
        writeIndex += bytesToWrite;


        if (length != bytesToWrite) // append tag and then write more
        {
            directBufferRW.wrap(resolvedAddress + ackIndex, TAG_SIZE_PER_CHUNK);
            directBufferRW.putByte(0, EMPTY_REGION_TAG);
            writeIndex += TAG_SIZE_PER_CHUNK;
            packRegions(src, srcIndex + bytesToWrite, length - bytesToWrite, consumedRegions, regionBuilders);
        }
        else if (consumedRegions.isEmpty()) // append empty tag and return
        {
            directBufferRW.wrap(resolvedAddress + ackIndex, TAG_SIZE_PER_CHUNK);
            directBufferRW.putByte(0, EMPTY_REGION_TAG);
            writeIndex += TAG_SIZE_PER_CHUNK;
        }
        else if(sizeOfRegions + TAG_SIZE_PER_CHUNK > transferCapacity - ackIndex) // append tags on wrap and return
        {
            directBufferRW.wrap(resolvedAddress + ackIndex, TAG_SIZE_PER_CHUNK);
            directBufferRW.putByte(0, WRAP_AROUND_REGION_TAG);

            int leftOverToWrite = transferCapacity - ackIndex - TAG_SIZE_PER_CHUNK;
            if (leftOverToWrite > 0)
            {
                directBufferRW.wrap(resolvedAddress + ackIndex + TAG_SIZE_PER_CHUNK, leftOverToWrite);
                directBufferRW.putBytes(
                    0,
                    consumedRegions.buffer(),
                    consumedRegions.offset(),
                    leftOverToWrite);
            }
            int rollOverToWrite = consumedRegions.sizeof() - leftOverToWrite;
            directBufferRW.wrap(resolvedAddress, rollOverToWrite);
            directBufferRW.putBytes(
                    0,
                    consumedRegions.buffer(),
                    consumedRegions.offset() + leftOverToWrite,
                    rollOverToWrite);

            writeIndex += TAG_SIZE_PER_CHUNK + sizeOfRegions;
        }
        else // append tags and return
        {
            directBufferRW.wrap(resolvedAddress + ackIndex, sizeOfRegions + TAG_SIZE_PER_CHUNK);
            directBufferRW.putByte(0, FULL_REGION_TAG);
            directBufferRW.putBytes(TAG_SIZE_PER_CHUNK, consumedRegions.buffer(), consumedRegions.offset(), sizeOfRegions);

            writeIndex += sizeOfRegions + TAG_SIZE_PER_CHUNK;
        }
    }

    public void buildAckedRegions(
        ListFW.Builder<RegionFW.Builder, RegionFW> builder,
        ListFW<RegionFW> regions)
    {
        regions.forEach(region ->
        {
            final long length = region.length();
            final long regionAddress = memoryManager.resolve(region.address());
            directBufferRW.wrap(regionAddress + length, TAG_SIZE_PER_CHUNK);
            ackIndex += length + TAG_SIZE_PER_CHUNK;

            switch (directBufferRW.getByte(0))
            {
                case EMPTY_REGION_TAG:
                    break;
                case FULL_REGION_TAG:
                {
                    final int remainingCapacity = (int) (resolvedAddress - regionAddress + transferCapacity);
                    directBufferRW.wrap(regionAddress + length + TAG_SIZE_PER_CHUNK, remainingCapacity);
                    regionsRO.wrap(directBufferRW, 0, remainingCapacity)
                             .forEach(ackedRegion -> builder.item(rb -> rb.address(ackedRegion.address())
                                                                          .length(ackedRegion.length())
                                                                          .streamId(ackedRegion.streamId())));
                    ackIndex += regionsRO.sizeof();
                    break;
                }
                case WRAP_AROUND_REGION_TAG:
                {
                    final int remaining = MAX_REGION_SIZE;
                    final int ackOffset = (int) (ackIndex & indexMask);
                    final int toEndOfBuffer = Math.min(transferCapacity - ackOffset, remaining);

                    directBufferBuilderRO.wrap(resolvedAddress + ackOffset, toEndOfBuffer);
                    directBufferBuilderRO.wrap(resolvedAddress, remaining - toEndOfBuffer);

                    DirectBuffer directBufferRO = directBufferBuilderRO.build();

                    regionsRO.wrap(directBufferRO, 0, MAX_REGION_SIZE)
                        .forEach(ackedRegion -> builder.item(rb -> rb.address(ackedRegion.address())
                                                                 .length(ackedRegion.length())
                                                                 .streamId(ackedRegion.streamId())));
                    ackIndex += regionsRO.sizeof();
                    break;
                }
                default:
                    throw new IllegalArgumentException("Invalid state");
            }
        });
    }

    public void release()
    {
        memoryManager.release(memoryAddress, transferCapacity);
    }
}