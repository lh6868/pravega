/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.emc.logservice.server.logs.operations;

import com.emc.logservice.common.StreamHelpers;
import com.emc.logservice.contracts.AppendContext;
import com.emc.logservice.server.logs.SerializationException;
import com.google.common.base.Preconditions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Log Operation that represents a StreamSegment Append.
 */
public class StreamSegmentAppendOperation extends StorageOperation {
    //region Members

    public static final byte OPERATION_TYPE = 1;
    private static final byte CURRENT_VERSION = 0;
    private long streamSegmentOffset;
    private byte[] data;
    private AppendContext appendContext;

    //endregion

    // region Constructor

    /**
     * Creates a new instance of the StreamSegmentAppendOperation class.
     *
     * @param streamSegmentId The Id of the StreamSegment to append to.
     * @param data            The payload to append.
     * @param appendContext   Append Context for this append.
     */
    public StreamSegmentAppendOperation(long streamSegmentId, byte[] data, AppendContext appendContext) {
        super(streamSegmentId);
        Preconditions.checkNotNull(data, "data");
        Preconditions.checkNotNull(appendContext, "appendContext");

        this.data = data;
        this.streamSegmentOffset = -1;
        this.appendContext = appendContext;
    }

    protected StreamSegmentAppendOperation(OperationHeader header, DataInputStream source) throws SerializationException {
        super(header, source);
    }

    //endregion

    //region StreamSegmentAppendOperation Properties

    /**
     * Gets a value indicating the Offset in the StreamSegment to append at.
     *
     * @return The offset.
     */
    public long getStreamSegmentOffset() {
        return this.streamSegmentOffset;
    }

    /**
     * Sets the Offset in the StreamSegment to append at.
     *
     * @param value The offset.
     */
    public void setStreamSegmentOffset(long value) {
        // No need for parameter validation here. We allow even invalid offsets now - we will check for them upon serialization.
        this.streamSegmentOffset = value;
    }

    /**
     * Gets the data buffer for this append.
     *
     * @return The data buffer.
     */
    public byte[] getData() {
        return this.data;
    }

    /**
     * Gets the AppendContext for this StreamSegmentAppendOperation, if any.
     *
     * @return The AppendContext, or null if no such context was defined.
     */
    public AppendContext getAppendContext() {
        return this.appendContext;
    }

    //endregion

    //region Operation Implementation

    @Override
    protected byte getOperationType() {
        return OPERATION_TYPE;
    }

    @Override
    protected void serializeContent(DataOutputStream target) throws IOException {
        ensureSerializationCondition(this.streamSegmentOffset >= 0, "StreamSegment Offset has not been assigned for this entry.");

        target.writeByte(CURRENT_VERSION);
        target.writeLong(getStreamSegmentId());
        target.writeLong(this.streamSegmentOffset);
        UUID clientId = this.appendContext.getClientId();
        target.writeLong(clientId.getMostSignificantBits());
        target.writeLong(clientId.getLeastSignificantBits());
        target.writeLong(this.appendContext.getEventNumber());
        target.writeInt(data.length);
        target.write(data, 0, data.length);
    }

    @Override
    protected void deserializeContent(DataInputStream source) throws IOException, SerializationException {
        byte version = readVersion(source, CURRENT_VERSION);
        setStreamSegmentId(source.readLong());
        this.streamSegmentOffset = source.readLong();
        long clientIdMostSig = source.readLong();
        long clientIdLeastSig = source.readLong();
        UUID clientId = new UUID(clientIdMostSig, clientIdLeastSig);
        long clientOffset = source.readLong();
        this.appendContext = new AppendContext(clientId, clientOffset);
        int dataLength = source.readInt();
        this.data = new byte[dataLength];
        int bytesRead = StreamHelpers.readAll(source, this.data, 0, this.data.length);
        assert bytesRead == this.data.length : "StreamHelpers.readAll did not read all the bytes requested.";
    }

    @Override
    public String toString() {
        return String.format(
                "%s, Offset = %s, Length = %d",
                super.toString(),
                toString(this.streamSegmentOffset, -1),
                this.data.length);
    }

    //endregion
}
