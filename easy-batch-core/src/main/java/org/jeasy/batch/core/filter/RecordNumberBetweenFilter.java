/**
 * The MIT License
 *
 *   Copyright (c) 2020, Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 */
package org.jeasy.batch.core.filter;

import org.jeasy.batch.core.record.Record;

/**
 * A {@link RecordFilter} that filters records
 * if their number is inside (inclusive) a given range.
 *
 * @author Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 *
 * @deprecated This class is deprecated since v5.3 and will be removed in v6.
 */
@Deprecated
public class RecordNumberBetweenFilter implements RecordFilter<Record> {

    protected long lowerBound;
    protected long higherBound;

    /**
     * Create a new {@link RecordNumberBetweenFilter}.
     *
     * @param lowerBound  Record number range lower bound.
     * @param higherBound Record number range higher bound.
     */
    public RecordNumberBetweenFilter(final long lowerBound, final long higherBound) {
        this.lowerBound = lowerBound;
        this.higherBound = higherBound;
    }

    @Override
    public Record processRecord(final Record record) {
        if (record.getHeader().getNumber() >= lowerBound && record.getHeader().getNumber() <= higherBound) {
            return null;
        }
        return record;
    }

}