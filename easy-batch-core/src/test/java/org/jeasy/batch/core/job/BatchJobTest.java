/*
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
package org.jeasy.batch.core.job;

import org.assertj.core.api.Assertions;
import org.jeasy.batch.core.filter.RecordFilter;
import org.jeasy.batch.core.jmx.JobMonitorMBean;
import org.jeasy.batch.core.listener.BatchListener;
import org.jeasy.batch.core.listener.JobListener;
import org.jeasy.batch.core.listener.PipelineListener;
import org.jeasy.batch.core.listener.RecordReaderListener;
import org.jeasy.batch.core.listener.RecordWriterListener;
import org.jeasy.batch.core.processor.RecordCollector;
import org.jeasy.batch.core.processor.RecordProcessor;
import org.jeasy.batch.core.reader.IterableRecordReader;
import org.jeasy.batch.core.reader.RecordReader;
import org.jeasy.batch.core.record.Batch;
import org.jeasy.batch.core.record.Record;
import org.jeasy.batch.core.validator.RecordValidator;
import org.jeasy.batch.core.writer.RecordWriter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BatchJobTest {

    private Job job;

    @Mock
    private Record<String> record1, record2;
    @Mock
    private RecordReader<String> reader;
    @Mock
    private RecordFilter<String> filter;
    @Mock
    private RecordValidator<String> validator;
    @Mock
    private RecordProcessor<String, String> firstProcessor, secondProcessor;
    @Mock
    private RecordWriter<String> writer;
    @Mock
    private JobReport jobReport;
    @Mock
    private JobListener jobListener1;
    @Mock
    private JobListener jobListener2;
    @Mock
    private BatchListener<String> batchListener;
    @Mock
    private RecordReaderListener<String> recordReaderListener;
    @Mock
    private RecordWriterListener<String> recordWriterListener;
    @Mock
    private PipelineListener pipelineListener;
    @Mock
    private Exception exception;
    @Mock
    private Error error;

    @Before
    public void setUp() throws Exception {
        when(reader.readRecord()).thenReturn(record1, record2, null);
        when(firstProcessor.processRecord(record1)).thenReturn(record1);
        when(firstProcessor.processRecord(record2)).thenReturn(record2);
        when(secondProcessor.processRecord(record1)).thenReturn(record1);
        when(secondProcessor.processRecord(record2)).thenReturn(record2);
        when(pipelineListener.beforeRecordProcessing(record1)).thenReturn(record1);
        when(pipelineListener.beforeRecordProcessing(record2)).thenReturn(record2);
        job = new JobBuilder<String, String>()
                .reader(reader)
                .processor(firstProcessor)
                .processor(secondProcessor)
                .writer(writer)
                .jobListener(jobListener1)
                .jobListener(jobListener2)
                .batchListener(batchListener)
                .readerListener(recordReaderListener)
                .writerListener(recordWriterListener)
                .pipelineListener(pipelineListener)
                .batchSize(2)
                .build();
    }

    /*
     * Core batch job implementation tests
     */

    @Test
    public void allComponentsShouldBeInvokedForEachRecordInOrder() throws Exception {

        new JobExecutor().execute(job);

        InOrder inOrder = Mockito.inOrder(reader, record1, record2, firstProcessor, secondProcessor, writer);

        inOrder.verify(reader).readRecord();
        inOrder.verify(firstProcessor).processRecord(record1);
        inOrder.verify(secondProcessor).processRecord(record1);

        inOrder.verify(reader).readRecord();
        inOrder.verify(firstProcessor).processRecord(record2);
        inOrder.verify(secondProcessor).processRecord(record2);

        inOrder.verify(writer).writeRecords(new Batch<>(record1, record2));
    }

    @Test
    public void readerShouldBeClosedAtTheEndOfExecution() throws Exception {
        job.call();

        verify(reader).close();
    }

    @Test
    public void writerShouldBeClosedAtTheEndOfExecution() throws Exception {
        job.call();

        verify(writer).close();
    }

    @Test
    public void whenNotAbleToOpenReader_ThenTheJobShouldFail() throws Exception {
        doThrow(exception).when(reader).open();

        JobReport jobReport = job.call();

        assertThat(jobReport).isNotNull();
        assertThat(jobReport.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(jobReport.getMetrics().getFilterCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getErrorCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getReadCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getWriteCount()).isEqualTo(0);
        assertThat(jobReport.getLastError()).isEqualTo(exception);
        verify(reader).close();
        verify(writer).close();
    }

    @Test
    public void whenNotAbleToOpenReaderDueToError_ThenTheJobShouldFail() throws Exception {
        doThrow(error).when(reader).open();

        JobReport jobReport = job.call();

        assertThat(jobReport).isNotNull();
        assertThat(jobReport.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(jobReport.getMetrics().getFilterCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getErrorCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getReadCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getWriteCount()).isEqualTo(0);
        assertThat(jobReport.getLastError()).isEqualTo(error);
        verify(reader).close();
        verify(writer).close();
    }

    @Test
    public void whenNotAbleToOpenWriter_ThenTheJobShouldFail() throws Exception {
        doThrow(exception).when(writer).open();

        JobReport jobReport = job.call();

        assertThat(jobReport).isNotNull();
        assertThat(jobReport.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(jobReport.getMetrics().getFilterCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getErrorCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getReadCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getWriteCount()).isEqualTo(0);
        assertThat(jobReport.getLastError()).isEqualTo(exception);
        verify(reader).close();
        verify(writer).close();
    }

    @Test
    public void whenNotAbleToOpenWriterDueToError_ThenTheJobShouldFail() throws Exception {
        doThrow(error).when(writer).open();

        JobReport jobReport = job.call();

        assertThat(jobReport).isNotNull();
        assertThat(jobReport.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(jobReport.getMetrics().getFilterCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getErrorCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getReadCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getWriteCount()).isEqualTo(0);
        assertThat(jobReport.getLastError()).isEqualTo(error);
        verify(reader).close();
        verify(writer).close();
    }

    @Test
    public void whenNotAbleToOpenReader_thenTheJobListenerShouldBeInvoked() throws Exception {
        doThrow(exception).when(reader).open();

        JobReport jobReportInternal = job.call();
        
        InOrder inOrder = inOrder(jobListener1, jobListener2, reader, writer);
        inOrder.verify(jobListener2).afterJob(jobReportInternal);
        inOrder.verify(jobListener1).afterJob(jobReportInternal);
        inOrder.verify(reader).close();
        inOrder.verify(writer).close();

    }

    @Test
    public void whenNotAbleToOpenReaderDueToError_thenTheJobListenerShouldBeInvoked() throws Exception {
        doThrow(error).when(reader).open();

        JobReport jobReportInternal = job.call();

        InOrder inOrder = inOrder(jobListener1, jobListener2, reader, writer);
        inOrder.verify(jobListener2).afterJob(jobReportInternal);
        inOrder.verify(jobListener1).afterJob(jobReportInternal);
        inOrder.verify(reader).close();
        inOrder.verify(writer).close();

    }

    @Test
    public void whenNotAbleToOpenWriter_thenTheJobListenerShouldBeInvoked() throws Exception {
        doThrow(exception).when(writer).open();

        JobReport jobReport = job.call();

        verify(jobListener1).afterJob(jobReport);
        verify(reader).close();
        verify(writer).close();
    }

    @Test
    public void whenNotAbleToOpenWriterDueToError_thenTheJobListenerShouldBeInvoked() throws Exception {
        doThrow(error).when(writer).open();

        JobReport jobReport = job.call();

        verify(jobListener1).afterJob(jobReport);
        verify(reader).close();
        verify(writer).close();
    }

    @Test
    public void whenNotAbleToReadNextRecord_ThenTheJobShouldFail() throws Exception {
        when(reader.readRecord()).thenThrow(exception);

        JobReport jobReport = job.call();

        assertThat(jobReport.getMetrics().getFilterCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getErrorCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getReadCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getWriteCount()).isEqualTo(0);
        assertThat(jobReport.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(jobReport.getLastError()).isEqualTo(exception);
        verify(reader).close();
        verify(writer).close();
    }

    @Test
    public void whenNotAbleToWriteRecords_ThenTheJobShouldFail() throws Exception {
        when(pipelineListener.beforeRecordProcessing(record1)).thenReturn(record1);
        when(pipelineListener.beforeRecordProcessing(record2)).thenReturn(record2);
        doThrow(exception).when(writer).writeRecords(new Batch(record1, record2));

        JobReport jobReport = job.call();

        assertThat(jobReport.getMetrics().getFilterCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getErrorCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getReadCount()).isEqualTo(2);
        assertThat(jobReport.getMetrics().getWriteCount()).isEqualTo(0);
        assertThat(jobReport.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(jobReport.getLastError()).isEqualTo(exception);
        verify(reader).close();
        verify(writer).close();
    }

    @Test
    public void reportShouldBeCorrect() {
        when(pipelineListener.beforeRecordProcessing(record1)).thenReturn(record1);
        when(pipelineListener.beforeRecordProcessing(record2)).thenReturn(record2);

        JobReport jobReport = job.call();
        assertThat(jobReport.getMetrics().getFilterCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getErrorCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getReadCount()).isEqualTo(2);
        assertThat(jobReport.getMetrics().getWriteCount()).isEqualTo(2);
        assertThat(jobReport.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(jobReport.getLastError()).isNull();
    }

    @Test
    public void whenErrorThresholdIsExceeded_ThenTheJobShouldBeAborted() throws Exception {
        when(firstProcessor.processRecord(record1)).thenThrow(exception);
        when(firstProcessor.processRecord(record2)).thenThrow(exception);
        job = new JobBuilder<String, String>()
                .reader(reader)
                .writer(writer)
                .processor(firstProcessor)
                .errorThreshold(1)
                .build();

        JobReport jobReport = job.call();

        assertThat(jobReport.getMetrics().getFilterCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getErrorCount()).isEqualTo(2);
        assertThat(jobReport.getMetrics().getReadCount()).isEqualTo(2);
        assertThat(jobReport.getMetrics().getWriteCount()).isEqualTo(0);
        assertThat(jobReport.getStatus()).isEqualTo(JobStatus.FAILED);
        verify(reader).close();
        verify(writer).close();
    }

    @Test
    public void whenARecordProcessorReturnsNull_thenTheRecordShouldBeFiltered() throws Exception {
        when(reader.readRecord()).thenReturn(record1).thenReturn(null);
        when(firstProcessor.processRecord(record1)).thenReturn(null);

        JobReport jobReport = job.call();

        assertThat(jobReport.getMetrics().getFilterCount()).isEqualTo(1);
        assertThat(jobReport.getMetrics().getErrorCount()).isEqualTo(0);
        assertThat(jobReport.getMetrics().getReadCount()).isEqualTo(1);
        assertThat(jobReport.getMetrics().getWriteCount()).isEqualTo(0);
        assertThat(jobReport.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    /*
     * JMX tests
     */

    @Test
    public void whenJobNameIsNotSpecified_thenTheJmxMBeanShouldBeRegisteredWithDefaultJobName() throws Exception {
        job = new JobBuilder<String, String>().enableJmx(true).build();
        job.call();
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        assertThat(mbs.isRegistered(new ObjectName(JobMonitorMBean.JMX_MBEAN_NAME + "name=" + JobParameters.DEFAULT_JOB_NAME))).isTrue();
    }

    @Test
    public void whenJobNameIsSpecified_thenTheJmxMBeanShouldBeRegisteredWithTheGivenJobName() throws Exception {
        String name = "master";
        job = new JobBuilder<String, String>().enableJmx(true).named(name).build();
        job.call();
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        assertThat(mbs.isRegistered(new ObjectName(JobMonitorMBean.JMX_MBEAN_NAME + "name=" + name))).isTrue();
    }

    /*
     * ***************
     * Listeners tests
     * ***************
     */

    /*
     * Job listener
     */
    @Test
    public void jobListenerShouldBeInvoked() {
        job = new JobBuilder<String, String>()
                .reader(reader)
                .jobListener(jobListener1)
                .build();

        JobReport report = job.call();

        verify(jobListener1).beforeJob(any(JobParameters.class));
        verify(jobListener1).afterJob(report);
    }

    /*
     * Batch listener
     */
    @Test
    public void batchListenerShouldBeInvokedForEachBatch() throws Exception {
        when(reader.readRecord()).thenReturn(record1, record2, null);
        job = new JobBuilder<String, String>()
                .reader(reader)
                .writer(writer)
                .batchListener(batchListener)
                .batchSize(1)
                .build();

        job.call();

        Batch<String> batch1 = new Batch<>(singletonList(record1));
        Batch<String> batch2 = new Batch<>(singletonList(record2));

        InOrder inOrder = Mockito.inOrder(batchListener);
        inOrder.verify(batchListener).beforeBatchReading();
        inOrder.verify(batchListener).afterBatchProcessing(batch1);
        inOrder.verify(batchListener).afterBatchWriting(batch1);
        inOrder.verify(batchListener).beforeBatchReading();
        inOrder.verify(batchListener).afterBatchProcessing(batch2);
        inOrder.verify(batchListener).afterBatchWriting(batch2);
        inOrder.verify(batchListener).beforeBatchReading();
    }
    
    @Test
    public void multipleBatchListenerShouldBeInvokedForEachBatchInOrder() throws Exception {
        BatchListener<String> batchListener1 = mock(BatchListener.class);
        BatchListener<String> batchListener2 = mock(BatchListener.class);
        when(reader.readRecord()).thenReturn(record1, record2, null);
        job = new JobBuilder<String, String>()
                .reader(reader)
                .writer(writer)
                .batchListener(batchListener1)
                .batchListener(batchListener2)
                .batchSize(1)
                .build();

        job.call();

        Batch<String> batch1 = new Batch<>(singletonList(record1));
        Batch<String> batch2 = new Batch<>(singletonList(record2));

        InOrder inOrder = Mockito.inOrder(batchListener1, batchListener2);
        inOrder.verify(batchListener1).beforeBatchReading();
        inOrder.verify(batchListener2).beforeBatchReading();
        inOrder.verify(batchListener2).afterBatchProcessing(batch1);
        inOrder.verify(batchListener1).afterBatchProcessing(batch1);
        inOrder.verify(batchListener2).afterBatchWriting(batch1);
        inOrder.verify(batchListener1).afterBatchWriting(batch1);
        //--
        inOrder.verify(batchListener1).beforeBatchReading();
        inOrder.verify(batchListener2).beforeBatchReading();
        inOrder.verify(batchListener2).afterBatchProcessing(batch2);
        inOrder.verify(batchListener1).afterBatchProcessing(batch2);
        inOrder.verify(batchListener2).afterBatchWriting(batch2);
        inOrder.verify(batchListener1).afterBatchWriting(batch2);
    }

    @Test
    public void whenWriterThrowsException_thenBatchListenerShouldBeInvoked() throws Exception {
        when(reader.readRecord()).thenReturn(record1, record2, null);
        doThrow(exception).when(writer).writeRecords(new Batch<>(record1, record2));

        job = new JobBuilder<String, String>()
                .reader(reader)
                .writer(writer)
                .batchListener(batchListener)
                .batchSize(2)
                .build();

        job.call();

        Batch<String> batch = new Batch<>(record1, record2);
        verify(batchListener, times(1)).beforeBatchReading();
        verify(batchListener).onBatchWritingException(batch, exception);
    }

    /*
     * Reader listener
     */
    @Test
    public void recordReaderListenerShouldBeInvokedForEachRecord() throws Exception {
        when(reader.readRecord()).thenReturn(record1, record2, null);
        job = new JobBuilder<String, String>()
                .reader(reader)
                .readerListener(recordReaderListener)
                .build();

        job.call();

        verify(recordReaderListener, times(3)).beforeRecordReading();
        verify(recordReaderListener).afterRecordReading(record1);
        verify(recordReaderListener).afterRecordReading(record2);
    }

    @Test
    public void whenRecordReaderThrowException_thenReaderListenerShouldBeInvoked() throws Exception {
        when(reader.readRecord()).thenThrow(exception);
        job = new JobBuilder<String, String>()
                .reader(reader)
                .readerListener(recordReaderListener)
                .build();

        job.call();

        verify(recordReaderListener).onRecordReadingException(exception);
    }

    /*
     * Writer listener
     */
    @Test
    public void recordWriterListenerShouldBeInvokedForEachBatch() throws Exception {
        when(reader.readRecord()).thenReturn(record1, record2, null);
        job = new JobBuilder<String, String>()
                .reader(reader)
                .writer(writer)
                .writerListener(recordWriterListener)
                .batchSize(2)
                .build();

        job.call();

        Batch<String> batch = new Batch<>(record1, record2);
        verify(recordWriterListener).beforeRecordWriting(batch);
        verify(recordWriterListener).afterRecordWriting(batch);
    }

    @Test
    public void whenRecordWriterThrowException_thenWriterListenerShouldBeInvoked() throws Exception {
        Batch<String> batch = new Batch<>(record1, record2);
        doThrow(exception).when(writer).writeRecords(batch);
        job = new JobBuilder<String, String>()
                .reader(reader)
                .writer(writer)
                .writerListener(recordWriterListener)
                .build();

        job.call();

        verify(recordWriterListener).onRecordWritingException(batch, exception);
    }

    /*
     * Pipeline listener
     */
    @Test
    public void pipelineListenerShouldBeInvokedForEachRecord() {

        when(pipelineListener.beforeRecordProcessing(record1)).thenReturn(record1);
        when(pipelineListener.beforeRecordProcessing(record2)).thenReturn(record2);

        job = new JobBuilder<String, String>()
                .reader(reader)
                .pipelineListener(pipelineListener)
                .build();

        job.call();

        verify(pipelineListener).beforeRecordProcessing(record1);
        verify(pipelineListener).afterRecordProcessing(record1, record1);
        verify(pipelineListener).beforeRecordProcessing(record2);
        verify(pipelineListener).afterRecordProcessing(record2, record2);
    }

    @Test
    public void whenProcessorThrowsException_thenPipelineListenerShouldBeInvoked() throws Exception {
        when(pipelineListener.beforeRecordProcessing(record1)).thenReturn(record1);
        when(firstProcessor.processRecord(record1)).thenThrow(exception);

        job = new JobBuilder<String, String>()
                .reader(reader)
                .processor(firstProcessor)
                .pipelineListener(pipelineListener)
                .build();

        job.call();

        verify(pipelineListener).onRecordProcessingException(record1, exception);
    }
    
    @Test
    public void allJobListenersShouldBeInvokedForEachRecordInOrder() throws Exception {

        JobReport jobReportReturned = job.call();

        InOrder inOrder = Mockito.inOrder(reader, firstProcessor, secondProcessor, jobListener1, jobListener2);

        inOrder.verify(jobListener1).beforeJob(any(JobParameters.class));
        inOrder.verify(jobListener2).beforeJob(any(JobParameters.class));

        inOrder.verify(reader).readRecord();
        inOrder.verify(firstProcessor).processRecord(record1);
        inOrder.verify(secondProcessor).processRecord(record1);
        inOrder.verify(reader).readRecord();
        inOrder.verify(firstProcessor).processRecord(record2);
        inOrder.verify(secondProcessor).processRecord(record2);
        inOrder.verify(reader).close();
        inOrder.verify(jobListener2).afterJob(jobReportReturned);
        inOrder.verify(jobListener1).afterJob(jobReportReturned);
    }
    
    @Test
    public void allRecordReaderListenersShouldBeInvokedForEachRecordInOrder() throws Exception {

        RecordReaderListener<String> readerListener1 = mock(RecordReaderListener.class);
        RecordReaderListener<String> readerListener2 = mock(RecordReaderListener.class);
        new JobBuilder<String, String>()
                .reader(reader)
                .processor(firstProcessor)
                .processor(secondProcessor)
                .readerListener(readerListener1)
                .readerListener(readerListener2)
                .build().call();

        InOrder inOrder = Mockito.inOrder(reader, firstProcessor, secondProcessor, readerListener1, readerListener2);

        inOrder.verify(readerListener1).beforeRecordReading();
        inOrder.verify(readerListener2).beforeRecordReading();
        inOrder.verify(reader).readRecord();
        inOrder.verify(readerListener2).afterRecordReading(record1);
        inOrder.verify(readerListener1).afterRecordReading(record1);
        inOrder.verify(firstProcessor).processRecord(record1);
        inOrder.verify(secondProcessor).processRecord(record1);
        inOrder.verify(readerListener1).beforeRecordReading();
        inOrder.verify(readerListener2).beforeRecordReading();
        inOrder.verify(reader).readRecord();
        inOrder.verify(readerListener2).afterRecordReading(record2);
        inOrder.verify(readerListener1).afterRecordReading(record2);
        inOrder.verify(firstProcessor).processRecord(record2);
        inOrder.verify(secondProcessor).processRecord(record2);
        inOrder.verify(reader).close();
    }

    @Test
    public void allRecordWriterListenersShouldBeInvokedForEachRecordInOrder() throws Exception {

        RecordWriterListener<String> writerListener1 = mock(RecordWriterListener.class);
        RecordWriterListener<String> writerListener2 = mock(RecordWriterListener.class);
        new JobBuilder<String, String>()
                .reader(reader)
                .processor(firstProcessor)
                .processor(secondProcessor)
                .writerListener(writerListener1)
                .writerListener(writerListener2)
                .batchSize(2)
                .writer(writer)
                .build().call();

        Batch<String> batch = new Batch<>(record1, record2);
        InOrder inOrder = Mockito.inOrder(reader, writer, firstProcessor, secondProcessor, writerListener1, writerListener2);

        
        inOrder.verify(writer).open();
        
        inOrder.verify(reader).readRecord();
        inOrder.verify(firstProcessor).processRecord(record1);
        inOrder.verify(secondProcessor).processRecord(record1);
        inOrder.verify(reader).readRecord();
        inOrder.verify(firstProcessor).processRecord(record2);
        inOrder.verify(secondProcessor).processRecord(record2);
        
        inOrder.verify(writerListener1).beforeRecordWriting(batch);
        inOrder.verify(writerListener2).beforeRecordWriting(batch);
        inOrder.verify(writer).writeRecords(batch);
        inOrder.verify(writerListener2).afterRecordWriting(batch);
        inOrder.verify(writerListener1).afterRecordWriting(batch);
        inOrder.verify(reader).close();
        inOrder.verify(writer).close();
    }
    

    @Test
    public void allPipelineListenersShouldBeInvokedForEachRecordInOrder() throws Exception {

        PipelineListener pipelineListener1 = mock(PipelineListener.class);
        PipelineListener pipelineListener2 = mock(PipelineListener.class);

        doReturn(record1).when(pipelineListener1).beforeRecordProcessing(record1);
        doReturn(record2).when(pipelineListener1).beforeRecordProcessing(record2);
        doReturn(record1).when(pipelineListener2).beforeRecordProcessing(record1);
        doReturn(record2).when(pipelineListener2).beforeRecordProcessing(record2);
        doNothing().when(pipelineListener1).afterRecordProcessing(record1, record1);
        doNothing().when(pipelineListener1).afterRecordProcessing(record2, record2);
        doNothing().when(pipelineListener2).afterRecordProcessing(record1, record1);
        doNothing().when(pipelineListener2).afterRecordProcessing(record2, record2);
        
         new JobBuilder<String, String>()
                .reader(reader)
                .processor(firstProcessor)
                .processor(secondProcessor)
                .pipelineListener(pipelineListener1)
                .pipelineListener(pipelineListener2)
                .build().call();

        InOrder inOrder = Mockito.inOrder(reader, firstProcessor, secondProcessor, pipelineListener1, pipelineListener2);

        inOrder.verify(reader).readRecord();
        inOrder.verify(pipelineListener1).beforeRecordProcessing(record1);
        inOrder.verify(pipelineListener2).beforeRecordProcessing(record1);
        inOrder.verify(firstProcessor).processRecord(record1);
        inOrder.verify(secondProcessor).processRecord(record1);
        inOrder.verify(pipelineListener2).afterRecordProcessing(record1, record1);
        inOrder.verify(pipelineListener1).afterRecordProcessing(record1, record1);

        inOrder.verify(reader).readRecord();
        inOrder.verify(pipelineListener1).beforeRecordProcessing(record2);
        inOrder.verify(pipelineListener2).beforeRecordProcessing(record2);
        inOrder.verify(firstProcessor).processRecord(record2);
        inOrder.verify(secondProcessor).processRecord(record2);
        inOrder.verify(pipelineListener2).afterRecordProcessing(record2, record2);
        inOrder.verify(pipelineListener1).afterRecordProcessing(record2, record2);

        inOrder.verify(reader).close();
    }

    @Test
    public void whenPreProcessorReturnsNull_thenTheRecordShouldBeSkipped() throws Exception {
        when(pipelineListener.beforeRecordProcessing(record1)).thenReturn(record1);
        when(pipelineListener.beforeRecordProcessing(record2)).thenReturn(null);
        when(firstProcessor.processRecord(record1)).thenReturn(record1);

        job = new JobBuilder<String, String>()
                .reader(reader)
                .processor(firstProcessor)
                .pipelineListener(pipelineListener)
                .build();

        job.call();

        verify(firstProcessor, times(1)).processRecord(record1);
        verify(firstProcessor, never()).processRecord(record2);
        verify(pipelineListener).afterRecordProcessing(record1, record1);
        verify(pipelineListener).afterRecordProcessing(record2, null);
    }

    /*
     * Batch scanning tests
     */

    @Test
    public void whenWriterThrowsExceptionAndBatchScanningIsActivated_thenShouldRewriteRecordsOneByOne() {
        class SavingRecordWriter implements RecordWriter<Integer> {
            private List<Batch<Integer>> batches = new ArrayList<>();

            @Override
            public void writeRecords(Batch<Integer> batch) throws Exception {
                batches.add(batch);
                if (batch.size() == 2) {
                    throw new Exception("Expected");
                }
            }

            public List<Batch<Integer>> getBatches() {
                return batches;
            }
        }

        IterableRecordReader<Integer> recordReader = new IterableRecordReader<>(Arrays.asList(1, 2, 3, 4));
        SavingRecordWriter recordWriter = new SavingRecordWriter();
        Job job = new JobBuilder<Integer, Integer>()
                .batchSize(2)
                .enableBatchScanning(true)
                .reader(recordReader)
                .writer(recordWriter)
                .build();

        // when
        job.call();

        // then
        // Expected result: 6 batches: [1,2], [1], [2], [3,4], [3], [4]

        List<Batch<Integer>> batches = recordWriter.getBatches();
        Assertions.assertThat(batches).hasSize(6);

        // batch 1: [1,2]
        Batch<Integer> batch = batches.get(0);
        Iterator<Record<Integer>> iterator = batch.iterator();
        Record<Integer> record = iterator.next();
        Assertions.assertThat(record.getHeader().getNumber()).isEqualTo(1);
        Assertions.assertThat(record.getHeader().isScanned()).isTrue();
        Assertions.assertThat(record.getPayload()).isEqualTo(1);

        record = iterator.next();
        Assertions.assertThat(record.getHeader().getNumber()).isEqualTo(2);
        Assertions.assertThat(record.getHeader().isScanned()).isTrue();
        Assertions.assertThat(record.getPayload()).isEqualTo(2);

        Assertions.assertThat(iterator.hasNext()).isFalse();

        // batch 2: [1] (scanned record)
        batch = batches.get(1);
        iterator = batch.iterator();
        record = iterator.next();
        Assertions.assertThat(record.getHeader().getNumber()).isEqualTo(1);
        Assertions.assertThat(record.getHeader().isScanned()).isTrue();
        Assertions.assertThat(record.getPayload()).isEqualTo(1);
        Assertions.assertThat(iterator.hasNext()).isFalse();

        // batch 3: [2] (scanned record)
        batch = batches.get(2);
        iterator = batch.iterator();
        record = iterator.next();
        Assertions.assertThat(record.getHeader().getNumber()).isEqualTo(2);
        Assertions.assertThat(record.getHeader().isScanned()).isTrue();
        Assertions.assertThat(record.getPayload()).isEqualTo(2);
        Assertions.assertThat(iterator.hasNext()).isFalse();

        // batch 4: [3,4]
        batch = batches.get(3);
        iterator = batch.iterator();
        record = iterator.next();
        Assertions.assertThat(record.getHeader().getNumber()).isEqualTo(3);
        Assertions.assertThat(record.getHeader().isScanned()).isTrue();
        Assertions.assertThat(record.getPayload()).isEqualTo(3);

        record = iterator.next();
        Assertions.assertThat(record.getHeader().getNumber()).isEqualTo(4);
        Assertions.assertThat(record.getHeader().isScanned()).isTrue();
        Assertions.assertThat(record.getPayload()).isEqualTo(4);

        Assertions.assertThat(iterator.hasNext()).isFalse();

        // batch 5: [3] (scanned record)
        batch = batches.get(4);
        iterator = batch.iterator();
        record = iterator.next();
        Assertions.assertThat(record.getHeader().getNumber()).isEqualTo(3);
        Assertions.assertThat(record.getHeader().isScanned()).isTrue();
        Assertions.assertThat(record.getPayload()).isEqualTo(3);
        Assertions.assertThat(iterator.hasNext()).isFalse();

        // batch 6: [4] (scanned record)
        batch = batches.get(5);
        iterator = batch.iterator();
        record = iterator.next();
        Assertions.assertThat(record.getHeader().getNumber()).isEqualTo(4);
        Assertions.assertThat(record.getHeader().isScanned()).isTrue();
        Assertions.assertThat(record.getPayload()).isEqualTo(4);
        Assertions.assertThat(iterator.hasNext()).isFalse();
    }

    /*
     * Job Interruption tests
     *
     * FIXME Is there a better way to test this ?
     */

    @Test
    @Ignore("This test may fail if the interruption signal is intercepted after starting the second batch")
    public void whenAJobIsInterrupted_thenNextBatchesShouldBeIgnored() throws Exception {
        // Given
        RecordCollector<Integer> recordCollector = new RecordCollector<>();
        List<Integer> dataSource = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            dataSource.add(i);
        }

        Job job = new JobBuilder<Integer, Integer>()
                .reader(new IterableRecordReader<>(dataSource))
                .processor(recordCollector)
                .batchSize(500000)
                .build();

        // When
        JobExecutor jobExecutor = new JobExecutor();
        Future<JobReport> jobReportFuture = jobExecutor.submit(job);

        Thread.sleep(50); // prevent aborting the job before even starting
        jobReportFuture.cancel(true);
        jobExecutor.awaitTermination(5, TimeUnit.SECONDS);

        // Then

        // can't assert on job report because jobReportFuture.get throws java.util.concurrent.CancellationException since it is cancelled
        assertThat(recordCollector.getRecords()).hasSize(500000);
    }

    @Test
    @Ignore("This test may fail if the interruption signal is intercepted after starting the second job")
    public void whenAJobIsInterrupted_thenOtherJobsShouldNotBeInterrupted() throws Exception {
        // Given
        RecordCollector<Integer> recordCollector1 = new RecordCollector<>();
        RecordCollector<Integer> recordCollector2 = new RecordCollector<>();
        List<Integer> dataSource = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            dataSource.add(i);
        }

        Job job1 = new JobBuilder<Integer, Integer>()
                .named("job1")
                .reader(new IterableRecordReader<>(dataSource))
                .processor(recordCollector1)
                .batchSize(500000)
                .build();
        Job job2 = new JobBuilder<Integer, Integer>()
                .named("job2")
                .reader(new IterableRecordReader<>(dataSource))
                .processor(recordCollector2)
                .batchSize(500000)
                .build();

        // When
        JobExecutor jobExecutor = new JobExecutor();
        Future<JobReport> jobReportFuture1 = jobExecutor.submit(job1);
        Future<JobReport> jobReportFuture2 = jobExecutor.submit(job2);

        Thread.sleep(50); // prevent aborting the job before even starting
        jobReportFuture1.cancel(true);
        jobExecutor.awaitTermination(5, TimeUnit.SECONDS);

        // Then

        // can't assert on job report because jobReportFuture.get throws java.util.concurrent.CancellationException since it is cancelled
        assertThat(recordCollector1.getRecords()).hasSize(500000);

        JobReport jobReport2 = jobReportFuture2.get();
        assertThat(jobReport2.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(recordCollector2.getRecords()).hasSize(1000000);
    }

}
