/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package picard.sam;

import htsjdk.samtools.BAMRecordCodec;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordQueryNameComparator;
import htsjdk.samtools.SAMRecordUtil;
import htsjdk.samtools.SAMTag;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.filter.FilteringIterator;
import htsjdk.samtools.filter.SamRecordFilter;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.FastqQualityFormat;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.PeekableIterator;
import htsjdk.samtools.util.ProgressLogger;
import htsjdk.samtools.util.QualityEncodingDetector;
import htsjdk.samtools.util.SolexaQualityConverter;
import htsjdk.samtools.util.SortingCollection;
import picard.PicardException;
import picard.cmdline.CommandLineProgram;
import picard.cmdline.CommandLineProgramProperties;
import picard.cmdline.Option;
import picard.cmdline.StandardOptionDefinitions;
import picard.cmdline.programgroups.SamOrBam;
import picard.util.TabbedTextFileWithHeaderParser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reverts a SAM file by optionally restoring original quality scores and by removing
 * all alignment information.
 */
@CommandLineProgramProperties(
        usage = RevertSam.USAGE_SUMMARY + RevertSam.USAGE_DETAILS,
        usageShort = RevertSam.USAGE_SUMMARY,
        programGroup = SamOrBam.class
)
public class RevertSam extends CommandLineProgram {
    static final String USAGE_SUMMARY ="Reverts SAM or BAM files to a previous state.  ";
    static final String USAGE_DETAILS ="This tool removes or restores certain properties of the SAM records, including alignment " +
            "information, which can be used to produce an unmapped BAM (uBAM) from a previously aligned BAM. It is also capable of " +
            "restoring the original quality scores of a BAM file that has already undergone base quality score recalibration (BQSR) if the" +
            "original qualities were retained." +
            "<h4>Example with single output:</h4>" +
            "<pre>" +
            "java -jar picard.jar RevertSam \\<br />" +
            "     I=input.bam \\<br />" +
            "     O=reverted.bam" +
            "</pre>" +
            "Output format is BAM by default, or SAM or CRAM if the input path ends with '.sam' or '.cram', respectively." +
            "<h4>Example outputting by read group with output map:</h4>" +
            "<pre>" +
            "java -jar picard.jar RevertSam \\<br />" +
            "     I=input.bam \\<br />" +
            "     OUTPUT_BY_READGROUP=true \\<br />" +
            "     OUTPUT_MAP=reverted_bam_paths.tsv" +
            "</pre>" +
            "Will output a BAM/SAM file per read group. By default, all outputs will be in BAM format. " +
            "However, a SAM file will be produced instead for any read group mapped in OUTPUT_MAP to a path ending with '.sam'. " +
            "A CRAM file will be produced for any read group mapped to a path ending with '.cram'. " +
            "<h4>Example outputting by read group without output map:</h4>" +
            "<pre>" +
            "java -jar picard.jar RevertSam \\<br />" +
            "     I=input.bam \\<br />" +
            "     OUTPUT_BY_READGROUP=true \\<br />" +
            "     O=/write/reverted/read/group/bams/in/this/dir" +
            "</pre>" +
            "Will output a BAM/SAM file per read group. By default, all outputs will be in BAM format. " +
            "However, outputs will be in SAM format if the input path ends with '.sam', or CRAM format if it ends with '.cram'." +
            "<hr />";
    @Option(shortName = StandardOptionDefinitions.INPUT_SHORT_NAME, doc = "The input SAM/BAM file to revert the state of.")
    public File INPUT;

    @Option(mutex = {"OUTPUT_MAP"}, shortName = StandardOptionDefinitions.OUTPUT_SHORT_NAME, doc = "The output SAM/BAM file to create, or an output directory if OUTPUT_BY_READGROUP is true.")
    public File OUTPUT;
    
    @Option(mutex = {"OUTPUT"}, shortName = "OM", doc = "Tab separated file with two columns, READ_GROUP_ID and OUTPUT, providing file mapping only used if OUTPUT_BY_READGROUP is true.")
    public File OUTPUT_MAP;

    @Option(shortName = "OBR", doc = "When true, outputs each read group in a separate file.")
    public boolean OUTPUT_BY_READGROUP = false;

    @Option(shortName = "SO", doc = "The sort order to create the reverted output file with.")
    public SortOrder SORT_ORDER = SortOrder.queryname;

    @Option(shortName = StandardOptionDefinitions.USE_ORIGINAL_QUALITIES_SHORT_NAME, doc = "True to restore original qualities from the OQ field to the QUAL field if available.")
    public boolean RESTORE_ORIGINAL_QUALITIES = true;

    @Option(doc = "Remove duplicate read flags from all reads.  Note that if this is true and REMOVE_ALIGNMENT_INFORMATION==false, " +
            " the output may have the unusual but sometimes desirable trait of having unmapped reads that are marked as duplicates.")
    public boolean REMOVE_DUPLICATE_INFORMATION = true;

    @Option(doc = "Remove all alignment information from the file.")
    public boolean REMOVE_ALIGNMENT_INFORMATION = true;

    @Option(doc = "When removing alignment information, the set of optional tags to remove.")
    public List<String> ATTRIBUTE_TO_CLEAR = new ArrayList<String>() {{
        add(SAMTag.NM.name());
        add(SAMTag.UQ.name());
        add(SAMTag.PG.name());
        add(SAMTag.MD.name());
        add(SAMTag.MQ.name());
        add(SAMTag.SA.name()); // Supplementary alignment metadata
        add(SAMTag.MC.name());      // Mate Cigar
        add(SAMTag.AS.name());
    }};

    @Option(doc = "WARNING: This option is potentially destructive. If enabled will discard reads in order to produce " +
            "a consistent output BAM. Reads discarded include (but are not limited to) paired reads with missing " +
            "mates, duplicated records, records with mismatches in length of bases and qualities. This option can " +
            "only be enabled if the output sort order is queryname and will always cause sorting to occur.")
    public boolean SANITIZE = false;

    @Option(doc = "If SANITIZE=true and higher than MAX_DISCARD_FRACTION reads are discarded due to sanitization then" +
            "the program will exit with an Exception instead of exiting cleanly. Output BAM will still be valid.")
    public double MAX_DISCARD_FRACTION = 0.01;

    @Option(doc = "The sample alias to use in the reverted output file.  This will override the existing " +
            "sample alias in the file and is used only if all the read groups in the input file have the " +
            "same sample alias ", shortName = StandardOptionDefinitions.SAMPLE_ALIAS_SHORT_NAME, optional = true)
    public String SAMPLE_ALIAS;

    @Option(doc = "The library name to use in the reverted output file.  This will override the existing " +
            "sample alias in the file and is used only if all the read groups in the input file have the " +
            "same library name ", shortName = StandardOptionDefinitions.LIBRARY_NAME_SHORT_NAME, optional = true)
    public String LIBRARY_NAME;

    private final static Log log = Log.getInstance(RevertSam.class);

    /** Default main method impl. */
    public static void main(final String[] args) {
        new RevertSam().instanceMainWithExit(args);
    }

    /**
     * Enforce that output ordering is queryname when sanitization is turned on since it requires a queryname sort.
     */
    @Override
    protected String[] customCommandLineValidation() {
        final List<String> errors = new ArrayList<String>();
        ValidationUtil.validateSanitizeSortOrder(SANITIZE, SORT_ORDER, errors);
        ValidationUtil.validateOutputParams(OUTPUT_BY_READGROUP, OUTPUT, OUTPUT_MAP, errors);

        if (!errors.isEmpty()) {
            return errors.toArray(new String[errors.size()]);
        }
        return null;
    }

    protected int doWork() {
        IOUtil.assertFileIsReadable(INPUT);
        ValidationUtil.assertWritable(OUTPUT, OUTPUT_BY_READGROUP);

        final boolean sanitizing = SANITIZE;
        final SamReader in = SamReaderFactory.makeDefault().referenceSequence(REFERENCE_SEQUENCE).validationStringency(VALIDATION_STRINGENCY).open(INPUT);
        final SAMFileHeader inHeader = in.getFileHeader();
        ValidationUtil.validateHeaderOverrides(inHeader, SAMPLE_ALIAS, LIBRARY_NAME);

        ////////////////////////////////////////////////////////////////////////////
        // Build the output writer with an appropriate header based on the options
        ////////////////////////////////////////////////////////////////////////////
        final boolean presorted = isPresorted(inHeader, SORT_ORDER, sanitizing);
        if (SAMPLE_ALIAS != null) overwriteSample(inHeader.getReadGroups(), SAMPLE_ALIAS);
        if (LIBRARY_NAME != null) overwriteLibrary(inHeader.getReadGroups(), LIBRARY_NAME);
        final SAMFileHeader singleOutHeader = createOutHeader(inHeader, SORT_ORDER, REMOVE_ALIGNMENT_INFORMATION);
        inHeader.getReadGroups().forEach(readGroup -> singleOutHeader.addReadGroup(readGroup));

        final Map<String, File> outputMap;
        final Map<String, SAMFileHeader> headerMap;
        if (OUTPUT_BY_READGROUP) {
            final String defaultExtension = getDefaultExtension(INPUT.toString());
            outputMap = createOutputMap(OUTPUT_MAP, OUTPUT, defaultExtension, inHeader.getReadGroups());
            ValidationUtil.assertAllReadGroupsMapped(outputMap, inHeader.getReadGroups());
            headerMap = createHeaderMap(inHeader, SORT_ORDER, REMOVE_ALIGNMENT_INFORMATION);
        } else {
            outputMap = null;
            headerMap = null;
        }

        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        final RevertSamWriter out = new RevertSamWriter(OUTPUT_BY_READGROUP, headerMap, outputMap, singleOutHeader, OUTPUT, presorted, factory, REFERENCE_SEQUENCE);
        
        ////////////////////////////////////////////////////////////////////////////
        // Build a sorting collection to use if we are sanitizing
        ////////////////////////////////////////////////////////////////////////////
        final RevertSamSorter sorter;
        if (sanitizing) sorter = new RevertSamSorter(OUTPUT_BY_READGROUP, headerMap, singleOutHeader, MAX_RECORDS_IN_RAM);
        else sorter = null;

        final ProgressLogger progress = new ProgressLogger(log, 1000000, "Reverted");
        for (final SAMRecord rec : in) {
            // Weed out non-primary and supplemental read as we don't want duplicates in the reverted file!
            if (rec.isSecondaryOrSupplementary()) continue;

            // log the progress before you revert because otherwise the "last read position" might not be accurate
            progress.record(rec);

            // Actually do the reverting of the remaining records
            revertSamRecord(rec);

            if (sanitizing) sorter.add(rec);
            else out.addAlignment(rec);
        }

        ////////////////////////////////////////////////////////////////////////////
        // Now if we're sanitizing, clean up the records and write them to the output
        ////////////////////////////////////////////////////////////////////////////
        if (!sanitizing) {
            out.close();
        } else {
            final Map<SAMReadGroupRecord, FastqQualityFormat> readGroupToFormat;
            try {
                readGroupToFormat = createReadGroupFormatMap(inHeader, REFERENCE_SEQUENCE, VALIDATION_STRINGENCY, INPUT, RESTORE_ORIGINAL_QUALITIES);
            } catch (final PicardException e) {
                log.error(e.getMessage());
                return -1;
            }

            final long[] sanitizeResults = sanitize(readGroupToFormat, sorter, out);
            final long discarded = sanitizeResults[0];
            final long total = sanitizeResults[1];
            out.close();

            final double discardRate = discarded / (double) total;
            final NumberFormat fmt = new DecimalFormat("0.000%");
            log.info("Discarded " + discarded + " out of " + total + " (" + fmt.format(discardRate) + ") reads in order to sanitize output.");

            if (discardRate > MAX_DISCARD_FRACTION) {
                throw new PicardException("Discarded " + fmt.format(discardRate) + " which is above MAX_DISCARD_FRACTION of " + fmt.format(MAX_DISCARD_FRACTION));
            }
        }

        CloserUtil.close(in);
        return 0;
    }

    static String getDefaultExtension(final String input) {
        if (input.endsWith(".sam")) {
            return ".sam";
        }
        if (input.endsWith(".cram")) {
            return ".cram";
        }
        return ".bam";
    }

    private boolean isPresorted(final SAMFileHeader inHeader, final SortOrder sortOrder, final boolean sanitizing) {
        return (inHeader.getSortOrder() == sortOrder) || (sortOrder == SortOrder.queryname && sanitizing);
    }

    /**
     * Takes an individual SAMRecord and applies the set of changes/reversions to it that
     * have been requested by program level options.
     */
    public void revertSamRecord(final SAMRecord rec) {
        if (RESTORE_ORIGINAL_QUALITIES) {
            final byte[] oq = rec.getOriginalBaseQualities();
            if (oq != null) {
                rec.setBaseQualities(oq);
                rec.setOriginalBaseQualities(null);
            }
        }

        if (REMOVE_DUPLICATE_INFORMATION) {
            rec.setDuplicateReadFlag(false);
        }

        if (REMOVE_ALIGNMENT_INFORMATION) {
            if (rec.getReadNegativeStrandFlag()) {
                SAMRecordUtil.reverseComplement(rec);
                rec.setReadNegativeStrandFlag(false);
            }

            // Remove all alignment based information about the read itself
            rec.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            rec.setAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
            rec.setCigarString(SAMRecord.NO_ALIGNMENT_CIGAR);
            rec.setMappingQuality(SAMRecord.NO_MAPPING_QUALITY);

            rec.setInferredInsertSize(0);
            rec.setNotPrimaryAlignmentFlag(false);
            rec.setProperPairFlag(false);
            rec.setReadUnmappedFlag(true);

            // Then remove any mate flags and info related to alignment
            rec.setMateAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
            rec.setMateNegativeStrandFlag(false);
            rec.setMateReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            rec.setMateUnmappedFlag(true);

            // And then remove any tags that are calculated from the alignment
            ATTRIBUTE_TO_CLEAR.forEach(tag -> rec.setAttribute(tag, null));
        }
    }

    private long[] sanitize(final Map<SAMReadGroupRecord, FastqQualityFormat> readGroupToFormat, final RevertSamSorter sorter, final RevertSamWriter out) {

        long total = 0, discarded = 0;
        final ProgressLogger sanitizerProgress = new ProgressLogger(log, 1000000, "Sanitized");

        final List<PeekableIterator<SAMRecord>> iterators = sorter.iterators();

        for (final PeekableIterator<SAMRecord> iterator : iterators) {
            readNameLoop:
            while (iterator.hasNext()) {
                final List<SAMRecord> recs = fetchByReadName(iterator);
                total += recs.size();

                // Check that all the reads have bases and qualities of the same length
                for (final SAMRecord rec : recs) {
                    if (rec.getReadBases().length != rec.getBaseQualities().length) {
                        log.debug("Discarding " + recs.size() + " reads with name " + rec.getReadName() + " for mismatching bases and quals length.");
                        discarded += recs.size();
                        continue readNameLoop;
                    }
                }

                // Check that if the first read is marked as unpaired that there is in fact only one read
                if (!recs.get(0).getReadPairedFlag() && recs.size() > 1) {
                    log.debug("Discarding " + recs.size() + " reads with name " + recs.get(0).getReadName() + " because they claim to be unpaired.");
                    discarded += recs.size();
                    continue readNameLoop;
                }

                // Check that if we have paired reads there is exactly one first of pair and one second of pair
                if (recs.get(0).getReadPairedFlag()) {
                    int firsts = 0, seconds = 0, unpaired = 0;
                    for (final SAMRecord rec : recs) {
                        if (!rec.getReadPairedFlag()) ++unpaired;
                        if (rec.getFirstOfPairFlag()) ++firsts;
                        if (rec.getSecondOfPairFlag()) ++seconds;
                    }

                    if (unpaired > 0 || firsts != 1 || seconds != 1) {
                        log.debug("Discarding " + recs.size() + " reads with name " + recs.get(0).getReadName() + " because pairing information in corrupt.");
                        discarded += recs.size();
                        continue readNameLoop;
                    }
                }

                // If we've made it this far spit the records into the output!
                for (final SAMRecord rec : recs) {
                    // The only valid quality score encoding scheme is standard; if it's not standard, change it.
                    final FastqQualityFormat recordFormat = readGroupToFormat.get(rec.getReadGroup());
                    if (!recordFormat.equals(FastqQualityFormat.Standard)) {
                        final byte[] quals = rec.getBaseQualities();
                        for (int i = 0; i < quals.length; i++) {
                            quals[i] -= SolexaQualityConverter.ILLUMINA_TO_PHRED_SUBTRAHEND;
                        }
                        rec.setBaseQualities(quals);
                    }
                    out.addAlignment(rec);
                    sanitizerProgress.record(rec);
                }
            }
        }
        return new long[]{discarded, total};
    }
    
    /**
     * Generates a list by consuming from the iterator in order starting with the first available
     * read and continuing while subsequent reads share the same read name. If there are no reads
     * remaining returns an empty list.
     */
    private List<SAMRecord> fetchByReadName(final PeekableIterator<SAMRecord> iterator) {
        final List<SAMRecord> out = new ArrayList<SAMRecord>();

        if (iterator.hasNext()) {
            final SAMRecord first = iterator.next();
            out.add(first);

            while (iterator.hasNext() && iterator.peek().getReadName().equals(first.getReadName())) {
                out.add(iterator.next());
            }
        }

        return out;
    }

    private void overwriteSample(final List<SAMReadGroupRecord> readGroups, final String sampleAlias) {
        readGroups.forEach(rg -> rg.setSample(sampleAlias));
    }

    private void overwriteLibrary(final List<SAMReadGroupRecord> readGroups, final String libraryName) {
        readGroups.forEach(rg -> rg.setLibrary(libraryName));
    }

    static Map<String, File> createOutputMap(
            final File outputMapFile,
            final File outputDir,
            final String defaultExtension,
            final List<SAMReadGroupRecord> readGroups) {

        final Map<String, File> outputMap;
        if (outputMapFile != null) {
            outputMap = createOutputMapFromFile(outputMapFile);
        } else {
            outputMap = createOutputMap(readGroups, outputDir, defaultExtension);
        }
        return outputMap;
    }

    private static Map<String, File> createOutputMapFromFile(final File outputMapFile) {
        final Map<String, File> outputMap = new HashMap<String, File>();
        final TabbedTextFileWithHeaderParser parser = new TabbedTextFileWithHeaderParser(outputMapFile);
        for (final TabbedTextFileWithHeaderParser.Row row : parser) {
            final String id = row.getField("READ_GROUP_ID");
            final String output = row.getField("OUTPUT");
            final File outputPath = new File(output);
            outputMap.put(id, outputPath);
        }
        CloserUtil.close(parser);
        return outputMap;
    }

    private static Map<String, File> createOutputMap(final List<SAMReadGroupRecord> readGroups, final File outputDir, final String extension) {
        final Map<String, File> outputMap = new HashMap<String, File>();
        for (final SAMReadGroupRecord readGroup : readGroups) {
            final String id = readGroup.getId();
            final String fileName = id + extension;
            final Path outputPath = Paths.get(outputDir.toString(), fileName);
            outputMap.put(id, outputPath.toFile());
        }
        return outputMap;
    }

    private Map<String, SAMFileHeader> createHeaderMap(
            final SAMFileHeader inHeader,
            final SortOrder sortOrder,
            final boolean removeAlignmentInformation) {
        
        final Map<String, SAMFileHeader> headerMap = new HashMap<String, SAMFileHeader>();
        for (final SAMReadGroupRecord readGroup : inHeader.getReadGroups()) {
            final SAMFileHeader header = createOutHeader(inHeader, sortOrder, removeAlignmentInformation);
            header.addReadGroup(readGroup);
            headerMap.put(readGroup.getId(), header);
        }
        return headerMap;
    }

    private SAMFileHeader createOutHeader(
            final SAMFileHeader inHeader,
            final SAMFileHeader.SortOrder sortOrder,
            final boolean removeAlignmentInformation) {

        final SAMFileHeader outHeader = new SAMFileHeader();
        outHeader.setSortOrder(sortOrder);
        if (!removeAlignmentInformation) {
            outHeader.setSequenceDictionary(inHeader.getSequenceDictionary());
            outHeader.setProgramRecords(inHeader.getProgramRecords());
        }
        return outHeader;
    }

    private Map<SAMReadGroupRecord, FastqQualityFormat> createReadGroupFormatMap(
            final SAMFileHeader inHeader,
            final File referenceSequence,
            final ValidationStringency validationStringency,
            final File input,
            final boolean restoreOriginalQualities) {

        final Map<SAMReadGroupRecord, FastqQualityFormat> readGroupToFormat = new HashMap<SAMReadGroupRecord, FastqQualityFormat>();

        // Figure out the quality score encoding scheme for each read group.
        for (final SAMReadGroupRecord rg : inHeader.getReadGroups()) {
            final SamReader reader = SamReaderFactory.makeDefault().referenceSequence(referenceSequence).validationStringency(validationStringency).open(input);
            final SamRecordFilter filter = new SamRecordFilter() {
                public boolean filterOut(final SAMRecord rec) {
                    return !rec.getReadGroup().getId().equals(rg.getId());
                }

                public boolean filterOut(final SAMRecord first, final SAMRecord second) {
                    throw new UnsupportedOperationException();
                }
            };
            readGroupToFormat.put(rg, QualityEncodingDetector.detect(QualityEncodingDetector.DEFAULT_MAX_RECORDS_TO_ITERATE, new FilteringIterator(reader.iterator(), filter), restoreOriginalQualities));
            CloserUtil.close(reader);
        }
        for (final SAMReadGroupRecord r : readGroupToFormat.keySet()) {
            log.info("Detected quality format for " + r.getReadGroupId() + ": " + readGroupToFormat.get(r));
        }
        if (readGroupToFormat.values().contains(FastqQualityFormat.Solexa)) {
            throw new PicardException("No quality score encoding conversion implemented for " + FastqQualityFormat.Solexa);
        }

        return readGroupToFormat;
    }

    /**
     * Contains a map of writers used when OUTPUT_BY_READGROUP=true
     * and a single writer used when OUTPUT_BY_READGROUP=false.
     */
    private static class RevertSamWriter {
        private final Map<String, SAMFileWriter> writerMap = new HashMap<String, SAMFileWriter>();
        private final SAMFileWriter singleWriter;
        private final boolean outputByReadGroup;

        RevertSamWriter(
                final boolean outputByReadGroup,
                final Map<String, SAMFileHeader> headerMap,
                final Map<String, File> outputMap,
                final SAMFileHeader singleOutHeader,
                final File singleOutput,
                final boolean presorted,
                final SAMFileWriterFactory factory,
                final File referenceFasta) {

            this.outputByReadGroup = outputByReadGroup;
            if (outputByReadGroup) {
                singleWriter = null;
                for (final Map.Entry<String, File> outputMapEntry : outputMap.entrySet()) {
                    final String readGroupId = outputMapEntry.getKey();
                    final File output = outputMapEntry.getValue();
                    final SAMFileHeader header = headerMap.get(readGroupId);
                    final SAMFileWriter writer = factory.makeWriter(header, presorted, output, referenceFasta);
                    writerMap.put(readGroupId, writer);
                }
            } else {
                singleWriter = factory.makeWriter(singleOutHeader, presorted, singleOutput, referenceFasta);
            }
        }

        void addAlignment(final SAMRecord rec) {
            final SAMFileWriter writer;
            if (outputByReadGroup) {
                writer = writerMap.get(rec.getReadGroup().getId());
            } else {
                writer = singleWriter;
            }
            writer.addAlignment(rec);
        }

        void close() {
            if (outputByReadGroup) {
                for (final SAMFileWriter writer : writerMap.values()) {
                    writer.close();
                }
            } else {
                singleWriter.close();
            }
        }
    }

    /**
     * Contains a map of sorters used when OUTPUT_BY_READGROUP=true
     * and a single sorter used when OUTPUT_BY_READGROUP=false.
     */
    private static class RevertSamSorter {
        private final Map<String, SortingCollection<SAMRecord>> sorterMap = new HashMap<String, SortingCollection<SAMRecord>>();
        private final SortingCollection<SAMRecord> singleSorter;
        private final boolean outputByReadGroup;

        RevertSamSorter(
                final boolean outputByReadGroup,
                final Map<String, SAMFileHeader> headerMap,
                final SAMFileHeader singleOutHeader,
                final int maxRecordsInRam) {

            this.outputByReadGroup = outputByReadGroup;
            if (outputByReadGroup) {
                for (final Map.Entry<String, SAMFileHeader> entry : headerMap.entrySet()) {
                    final String readGroupId = entry.getKey();
                    final SAMFileHeader outHeader = entry.getValue();
                    final SortingCollection<SAMRecord> sorter = SortingCollection.newInstance(SAMRecord.class, new BAMRecordCodec(outHeader), new SAMRecordQueryNameComparator(), maxRecordsInRam);
                    sorterMap.put(readGroupId, sorter);
                }
                singleSorter = null;
            } else {
                singleSorter = SortingCollection.newInstance(SAMRecord.class, new BAMRecordCodec(singleOutHeader), new SAMRecordQueryNameComparator(), maxRecordsInRam);
            }
        }

        void add(final SAMRecord rec) {
            final SortingCollection<SAMRecord> sorter;
            if (outputByReadGroup) {
                sorter = sorterMap.get(rec.getReadGroup().getId());
            } else {
                sorter = singleSorter;
            }
            sorter.add(rec);
        }

        List<PeekableIterator<SAMRecord>> iterators() {
            final List<PeekableIterator<SAMRecord>> iterators = new ArrayList<PeekableIterator<SAMRecord>>();
            if (outputByReadGroup) {
                for (final SortingCollection<SAMRecord> sorter : sorterMap.values()) {
                    final PeekableIterator<SAMRecord> iterator = new PeekableIterator<SAMRecord>(sorter.iterator());
                    iterators.add(iterator);
                }
            } else {
                final PeekableIterator<SAMRecord> iterator = new PeekableIterator<SAMRecord>(singleSorter.iterator());
                iterators.add(iterator);
            }
            return iterators;
        }
    }

    /**
     * Methods used for validating parameters to RevertSam.
     */
    static class ValidationUtil {

        static void validateSanitizeSortOrder(final boolean sanitize, final SAMFileHeader.SortOrder sortOrder, final List<String> errors) {
            if (sanitize && sortOrder != SAMFileHeader.SortOrder.queryname) {
                errors.add("SORT_ORDER must be queryname when sanitization is enabled with SANITIZE=true.");
            }
        }

        static void validateOutputParams(final boolean outputByReadGroup, final File output, final File outputMap, final List<String> errors) {
            if (outputByReadGroup) {
                validateOutputParamsByReadGroup(output, outputMap, errors);
            } else {
                validateOutputParamsNotByReadGroup(output, outputMap, errors);
            }
        }

        static void validateOutputParamsByReadGroup(final File output, final File outputMap, final List<String> errors) {
            if (output != null) {
                if (!Files.isDirectory(output.toPath())) {
                    errors.add("When OUTPUT_BY_READGROUP=true and OUTPUT is provided, it must be a directory: " + output);
                }
                return;
            }
            // output is null if we reached here
            if (outputMap == null) {
                errors.add("Must provide either OUTPUT or OUTPUT_MAP when OUTPUT_BY_READGROUP=true.");
                return;
            }
            if (!Files.isReadable(outputMap.toPath())) {
                errors.add("Cannot read OUTPUT_MAP " + outputMap);
                return;
            }
            final TabbedTextFileWithHeaderParser parser = new TabbedTextFileWithHeaderParser(outputMap);
            if (!ValidationUtil.isOutputMapHeaderValid(parser.columnLabelsList())) {
                errors.add("Invalid header: " + outputMap + ". Must be a tab-separated file with READ_GROUP_ID as first column and OUTPUT as second column.");
            }
        }

        static void validateOutputParamsNotByReadGroup(final File output, final File outputMap, final List<String> errors) {
            if (outputMap != null) {
                errors.add("Cannot provide OUTPUT_MAP when OUTPUT_BY_READGROUP=false. Provide OUTPUT instead.");
            }
            if (output == null) {
                errors.add("OUTPUT is required when OUTPUT_BY_READGROUP=false");
                return;
            }
            if (Files.isDirectory(output.toPath())) {
                errors.add("OUTPUT " + output + " should not be a directory when OUTPUT_BY_READGROUP=false");
            }
        }

        /**
         * If we are going to override SAMPLE_ALIAS or LIBRARY_NAME, make sure all the read
         * groups have the same values.
         */
        static void validateHeaderOverrides(
                final SAMFileHeader inHeader,
                final String sampleAlias,
                final String libraryName) {

            final List<SAMReadGroupRecord> rgs = inHeader.getReadGroups();
            if (sampleAlias != null || libraryName != null) {
                boolean allSampleAliasesIdentical = true;
                boolean allLibraryNamesIdentical = true;
                for (int i = 1; i < rgs.size(); i++) {
                    if (!rgs.get(0).getSample().equals(rgs.get(i).getSample())) {
                        allSampleAliasesIdentical = false;
                    }
                    if (!rgs.get(0).getLibrary().equals(rgs.get(i).getLibrary())) {
                        allLibraryNamesIdentical = false;
                    }
                }
                if (sampleAlias != null && !allSampleAliasesIdentical) {
                    throw new PicardException("Read groups have multiple values for sample.  " +
                            "A value for SAMPLE_ALIAS cannot be supplied.");
                }
                if (libraryName != null && !allLibraryNamesIdentical) {
                    throw new PicardException("Read groups have multiple values for library name.  " +
                            "A value for library name cannot be supplied.");
                }
            }
        }

        static void assertWritable(final File output, final boolean outputByReadGroup) {
            if (outputByReadGroup) {
                if (output != null) {
                    IOUtil.assertDirectoryIsWritable(output);
                }
            } else {
                IOUtil.assertFileIsWritable(output);
            }
        }

        static void assertAllReadGroupsMapped(final Map<String, File> outputMap, final List<SAMReadGroupRecord> readGroups) {
            for (final SAMReadGroupRecord readGroup : readGroups) {
                final String id = readGroup.getId();
                final File output = outputMap.get(id);
                if (output == null) {
                    throw new PicardException("Read group id " + id + " not found in OUTPUT_MAP " + outputMap);
                }
            }
        }

        static boolean isOutputMapHeaderValid(final List<String> columnLabels) {
            if (columnLabels.size() < 2) {
                return false;
            }
            if (!"READ_GROUP_ID".equals(columnLabels.get(0))) {
                return false;
            }
            if (!"OUTPUT".equals(columnLabels.get(1))) {
                return false;
            }
            return true;
        }
    }
}
