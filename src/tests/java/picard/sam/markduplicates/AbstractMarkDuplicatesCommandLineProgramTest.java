/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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
package picard.sam.markduplicates;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;

/**
 * This class defines the individual test cases to run.  The actual running of the test is done
 * by AbstractMarkDuplicatesCommandLineProgramTester or children thereof (see getTester).
 */
public abstract class AbstractMarkDuplicatesCommandLineProgramTest {

    protected abstract AbstractMarkDuplicatesCommandLineProgramTester getTester();

    protected final static int DEFAULT_BASE_QUALITY = 10;

    protected boolean markSecondaryAndSupplementaryRecordsLikeTheCanonical() { return false; }

    protected boolean markUnmappedRecordsLikeTheirMates() { return false; }

    @Test
    public void testSingleUnmappedFragment() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addUnmappedFragment(-1, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testTwoUnmappedFragments() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addUnmappedFragment(-1, DEFAULT_BASE_QUALITY);
        tester.addUnmappedFragment(-1, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testSingleUnmappedPair() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addUnmappedPair(-1, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testSingleMappedFragment() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedFragment(1, 1, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testTwoMappedFragments() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedFragment(0, 1, false, DEFAULT_BASE_QUALITY);
        tester.addMappedFragment(0, 1, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.runTest();
    }

    @Test
    public void testSingleMappedPair() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testSingleMappedFragmentAndSingleMappedPair() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedFragment(1, 1, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairs() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.runTest();
    }

    @Test
    public void testThreeMappedPairs() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.runTest();
    }

    @Test
    public void testSingleMappedFragmentAndTwoMappedPairs() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedFragment(1, 1, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairsAndTerminalUnmappedFragment() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addUnmappedFragment(-1, DEFAULT_BASE_QUALITY); // unmapped fragment at end of file
        tester.runTest();
    }
    
    @Test
    public void testTwoMappedPairsAndTerminalUnmappedPair() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addUnmappedPair(-1, DEFAULT_BASE_QUALITY); // unmapped pair at end of file
        tester.runTest();
    }

    @Test
    public void testOpticalDuplicateFinding() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();

        // explicitly creating 1 expected optical duplicate pair
        tester.setExpectedOpticalDuplicate(1);

        // pass in the read names manually, in order to control duplicates vs optical duplicates
        tester.addMatePair("READ0:1:1:1:1", 1, 1, 100, false, false, false, false, "50M", "50M", false, true, false,
                           false, false, DEFAULT_BASE_QUALITY); // non-duplicate mapped pair to start
        tester.addMatePair("READ1:1:1:1:300", 1, 1, 100, false, false, true, true, "50M", "50M", false, true, false,
                           false, false, DEFAULT_BASE_QUALITY); // duplicate pair, NOT optical duplicate (delta-Y > 100)
        tester.addMatePair("READ2:1:1:1:50", 1, 1, 100, false, false, true, true, "50M", "50M", false, true, false,
                           false, false, DEFAULT_BASE_QUALITY); // duplicate pair, expected optical duplicate (delta-X and delta-Y < 100)
        tester.runTest();
    }

    @Test
    public void testOpticalDuplicateClusterSamePositionNoOpticalDuplicates() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.setExpectedOpticalDuplicate(0);
        tester.addMatePair("RUNID:7:1203:2886:82292", 1, 485253, 485253, false, false, true, true, "42M59S", "59S42M", false, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMatePair("RUNID:7:1203:2884:16834", 1, 485253, 485253, false, false, false, false, "59S42M", "42M59S", true, false, false, false, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }
    
    @Test
    public void testOpticalDuplicateClusterSamePositionNoOpticalDuplicatesWithinPixelDistance() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.setExpectedOpticalDuplicate(0);
        tester.addMatePair("RUNID:7:1203:2886:16834", 1, 485253, 485253, false, false, true, true, "42M59S", "59S42M", false, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMatePair("RUNID:7:1203:2884:16835", 1, 485253, 485253, false, false, false, false, "59S42M", "42M59S", true, false, false, false, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testOpticalDuplicateClusterSamePositionOneOpticalDuplicatesWithinPixelDistance() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.setExpectedOpticalDuplicate(1);
        tester.addMatePair("RUNID:7:1203:2886:16834", 1, 485253, 485253, false, false, true, true, "45M", "45M", false, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMatePair("RUNID:7:1203:2884:16835", 1, 485253, 485253, false, false, false, false, "45M", "45M", false, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testOpticalDuplicateClusterOneEndSamePositionOneCluster() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.setExpectedOpticalDuplicate(1);
        tester.addMatePair("RUNID:7:2205:17939:39728", 1, 485328, 485312, false, false, false, false, "55M46S", "30S71M", false, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMatePair("RUNID:7:2205:17949:39745", 1, 485328, 485328, false, false, true, true, "55M46S", "46S55M", false, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test(dataProvider = "secondarySupplementaryData")
    public void testManyOpticalDuplicateClusterOneEndSamePositionOneCluster(final Boolean additionalFragSecondary, final Boolean additionalFragSupplementary, final Boolean fragLikeFirst) {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.setExpectedOpticalDuplicate(2);
        //canonical
        tester.addMatePair("RUNID:7:2205:17939:39728", 1, 485328, 485312, false, false, false, false, "55M46S", "30S71M", false, true, false, false, false, DEFAULT_BASE_QUALITY);
        //library
        tester.addMatePair("RUNID:7:2205:27949:39745", 1, 485328, 485328, false, false, true, true, "55M46S", "46S55M", false, true, false, false, false, DEFAULT_BASE_QUALITY);
        //optical (of canonical)
        tester.addMatePair("RUNID:7:2205:17949:39745", 1, 485328, 485328, false, false, true, true, "55M46S", "46S55M", false, true, false, false, false, DEFAULT_BASE_QUALITY);

        //non-canonical
        tester.addMappedFragment(fragLikeFirst ? "RUNID:7:2205:17939:39728" : "RUNID:7:2205:17949:39745", 1, 400, markSecondaryAndSupplementaryRecordsLikeTheCanonical() && !fragLikeFirst, null, null, additionalFragSecondary, additionalFragSupplementary, DEFAULT_BASE_QUALITY);

        //library
        tester.addMatePair("RUNID:7:2205:37949:39745", 1, 485328, 485328, false, false, true, true, "55M46S", "46S55M", false, true, false, false, false, DEFAULT_BASE_QUALITY);
        //optical of canonical
        tester.addMatePair("RUNID:7:2205:17959:39735", 1, 485328, 485328, false, false, true, true, "55M46S", "46S55M", false, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairsAndMappedSecondaryFragment() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedFragment(1, 200, false, DEFAULT_BASE_QUALITY, true); // mapped non-primary fragment
        tester.runTest();
    }

    @Test
    public void testMappedFragmentAndMappedPairFirstOfPairNonPrimary() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedFragment(1, 1, false,DEFAULT_BASE_QUALITY);
        tester.addMatePair(1, 200, 0, false, true, false, false, "54M22S", null, false, false, true, true, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairsMatesSoftClipped() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 10022, 10051, false, false, "76M", "8S68M", false, true, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 10022, 10063, false, false, "76M", "5S71M", false, true, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairsWithSoftClipping() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        // NB: no duplicates
        // 5'1: 2, 5'2:46+73M=118
        // 5'1: 2, 5'2:51+68M=118
        tester.addMappedPair(1, 2, 46, false, false, "6S42M28S", "3S73M", false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 2, 51, true, true, "6S42M28S", "8S68M", false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairsWithSoftClippingFirstOfPairOnlyNoMateCigar() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.setNoMateCigars(true);
        // NB: no duplicates
        // 5'1: 2, 5'2:46+73M=118
        // 5'1: 2, 5'2:51+68M=118
        tester.addMappedPair(1, 12, 46, false, false, "6S42M28S", null, true, DEFAULT_BASE_QUALITY); // only add the first one
        tester.addMappedPair(1, 12, 51, false, false, "6S42M28S", null, true, DEFAULT_BASE_QUALITY); // only add the first one
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairsWithSoftClippingBoth() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        // mapped reference length: 73 + 42 = 115
        tester.addMappedPair(1, 10046, 10002, true, true, "3S73M", "6S42M28S", true, false, false, DEFAULT_BASE_QUALITY);
        // mapped reference length: 68 + 48 = 116
        tester.addMappedPair(1, 10051, 10002, false, false, "8S68M", "6S48M22S", true, false, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }
    
    @Test
    public void testMatePairSecondUnmapped() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMatePair(1, 10049, 10049, false, true, false, false, "11M2I63M", null, false, false, false, false, false, DEFAULT_BASE_QUALITY);   // neither are duplicates
        tester.runTest();
    }

    @Test
    public void testMatePairFirstUnmapped() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMatePair(1, 10056, 10056, true, false, false, false, null, "54M22S", false, false, false, false, false, DEFAULT_BASE_QUALITY);    // neither are duplicates
        tester.runTest();
    }

    @Test
    public void testMappedFragmentAndMatePairSecondUnmapped() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMatePair(1, 10049, 10049, false, true, false, false, "11M2I63M", null, false, false, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedFragment(1, 10049, true, DEFAULT_BASE_QUALITY); // duplicate
        tester.runTest();
    }
    
    @Test
    public void testMappedFragmentAndMatePairFirstUnmapped() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMatePair(1, 10049, 10049, true, false, false, false, null, "11M2I63M", false, false, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedFragment(1, 10049, true, DEFAULT_BASE_QUALITY); // duplicate
        tester.runTest();
    }

    @Test
    public void testMappedPairAndMatePairSecondUnmapped() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMatePair(1, 10040, 10040, false, true, true, markUnmappedRecordsLikeTheirMates(), "76M", null, false, false, false, false, false, DEFAULT_BASE_QUALITY); // second a duplicate,
        // second end unmapped
        tester.addMappedPair(1, 10189, 10040, false, false, "41S35M", "65M11S", true, false, false, DEFAULT_BASE_QUALITY); // mapped OK
        tester.runTest();
    }

    @Test
    public void testMappedPairAndMatePairFirstUnmapped() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMatePair(1, 10040, 10040, true, false, markUnmappedRecordsLikeTheirMates(), true,  null, "76M", false, false, false, false, false, DEFAULT_BASE_QUALITY); // first a duplicate,
        // first end unmapped
        tester.addMappedPair(1, 10189, 10040, false, false, "41S35M", "65M11S", true, false, false, DEFAULT_BASE_QUALITY); // mapped OK
        tester.runTest();
    }

    // TODO: fails on MarkDuplicatesWithMateCigar
    @Test
    public void testMappedPairAndMatePairFirstOppositeStrandSecondUnmapped() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        // first end mapped OK -, second end unmapped
        tester.addMatePair(1, 484071, 484071, false, true, false, false,  "66S35M", null, true, false, false, false, false, DEFAULT_BASE_QUALITY);
        // mapped OK +/-
        tester.addMappedPair(1, 484105, 484075, false, false, "35M66S", "30S71M", false, true, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }


    @Test
    public void testMappedPairAndMappedFragmentAndMatePairSecondUnmapped() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMatePair(1, 10040, 10040, false, true, true, markUnmappedRecordsLikeTheirMates(), "76M", null, false, false, false, false, false, DEFAULT_BASE_QUALITY); // first a duplicate,
        // second end unmapped
        tester.addMappedPair(1, 10189, 10040, false, false, "41S35M", "65M11S", true, false, false, DEFAULT_BASE_QUALITY); // mapped OK
        tester.addMappedFragment(1, 10040, true, DEFAULT_BASE_QUALITY); // duplicate
        tester.runTest();
    }

    @Test
    public void testMappedPairAndMappedFragmentAndMatePairFirstUnmapped() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMatePair(1, 10040, 10040, true, false, markUnmappedRecordsLikeTheirMates(), true, null, "76M", false, false, false, false, false, DEFAULT_BASE_QUALITY); // first a duplicate,
        // first end unmapped
        tester.addMappedPair(1, 10189, 10040, false, false, "41S35M", "65M11S", true, false, false, DEFAULT_BASE_QUALITY); // mapped OK
        tester.addMappedFragment(1, 10040, true, DEFAULT_BASE_QUALITY); // duplicate
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairsWithOppositeOrientations() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 10182, 10038, true, true, "32S44M", "66M10S", true, false, false, DEFAULT_BASE_QUALITY); // -/+
        tester.addMappedPair(1, 10038, 10182, false, false, "70M6S", "32S44M", false, true, false, DEFAULT_BASE_QUALITY); // +/-, both are duplicates
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairsWithOppositeOrientationsNumberTwo() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 10038, 10182, false, false, "70M6S", "32S44M", false, true, false, DEFAULT_BASE_QUALITY); // +/-, both are duplicates
        tester.addMappedPair(1, 10182, 10038, true, true, "32S44M", "66M10S", true, false, false, DEFAULT_BASE_QUALITY); // -/+
        tester.runTest();
    }
    
    @Test
    public void testThreeMappedPairsWithMatchingSecondMate() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        // Read0 and Read2 are duplicates
        // 10181+41=10220, 10058
        tester.addMappedPair(1, 10181, 10058, false, false, "41S35M", "47M29S", true, false, false, DEFAULT_BASE_QUALITY); // -/+
        // 10181+37=10216, 10058
        tester.addMappedPair(1, 10181, 10058, true, true, "37S39M", "44M32S", true, false, false, DEFAULT_BASE_QUALITY); // -/+
        // 10180+36=10216, 10058
        tester.addMappedPair(1, 10180, 10058, false, false, "36S40M", "50M26S", true, false, false, DEFAULT_BASE_QUALITY); // -/+, both are duplicates
        tester.runTest();
    }
    
    @Test
    public void testMappedPairWithSamePosition() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 4914, 4914, false, false, "37M39S", "73M3S", false, false, false, DEFAULT_BASE_QUALITY); // +/+
        tester.runTest();
    }

    @Test
    public void testMappedPairWithSamePositionSameCigar() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(1, 4914, 4914, false, false, "37M39S", "37M39S", false, false, false, DEFAULT_BASE_QUALITY); // +/+
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairWithSamePosition() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(0, 5604914, 5604914, false, false, "37M39S", "73M3S", false, false, false, DEFAULT_BASE_QUALITY); // +/+
        tester.addMappedPair(0, 5604914, 5604914, true, true, "37M39S", "73M3S", false, false, false, DEFAULT_BASE_QUALITY); // +/+
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairWithSamePositionDifferentStrands() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(0, 5604914, 5604914, false, false, "50M", "50M", true, false, false, DEFAULT_BASE_QUALITY); // +/-
        tester.addMappedPair(0, 5604914, 5604914, true, true, "50M", "50M", false, true, false, DEFAULT_BASE_QUALITY); // -/+
        tester.runTest();
    }

    @Test
    public void testTwoMappedPairWithSamePositionDifferentStrands2() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(0, 5604914, 5604915, false, false, "50M", "50M", true, false, false, DEFAULT_BASE_QUALITY); // +/-
        tester.addMappedPair(0, 5604915, 5604914, true, true, "50M", "50M", false, true, false, DEFAULT_BASE_QUALITY); // -/+
        tester.runTest();
    }

    @Test
    public void testMappedPairWithFirstEndSamePositionAndOther() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(0, 5604914, 5605914, false, false, "37M39S", "73M3S", false, false, false, DEFAULT_BASE_QUALITY); // +/+
        tester.addMappedPair(0, 5604914, 5604914, false, false, "37M39S", "73M3S", false, false, false, DEFAULT_BASE_QUALITY); // +/+
        tester.runTest();
    }

    @Test
    public void testTwoGroupsOnDifferentChromosomesOfTwoFragments() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedFragment(0, 1, false, DEFAULT_BASE_QUALITY);
        tester.addMappedFragment(0, 1, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedFragment(1, 1, false, DEFAULT_BASE_QUALITY);
        tester.addMappedFragment(1, 1, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.runTest();
    }

    @Test
    public void testTwoGroupsOnDifferentChromosomesOfTwoMappedPairs() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(0, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(0, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.runTest();
    }

    @Test
    public void testTwoGroupsOnDifferentChromosomesOfThreeMappedPairs() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(0, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(0, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(0, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.runTest();
    }

    @Test
    public void testThreeGroupsOnDifferentChromosomesOfThreeMappedPairs() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(0, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(0, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(0, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(1, 1, 100, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(1, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(2, 1, 100, false, false , DEFAULT_BASE_QUALITY);
        tester.addMappedPair(2, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.addMappedPair(2, 1, 100, true, true, DEFAULT_BASE_QUALITY); // duplicate!!!
        tester.runTest();
    }
    
    @Test
    public void testBulkFragmentsNoDuplicates() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        for(int position = 1; position <= 10000; position += 1) {
            tester.addMappedFragment(0, position, false, "100M", DEFAULT_BASE_QUALITY);
        }
        tester.runTest();
    }

    @Test
    public void testBulkFragmentsWithDuplicates() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        for(int position = 1; position <= 10000; position += 1) {
            tester.addMappedFragment(0, position, false, "100M", DEFAULT_BASE_QUALITY);
            tester.addMappedFragment(0, position, true, "100M", DEFAULT_BASE_QUALITY);
            tester.addMappedFragment(0, position, true, "100M", DEFAULT_BASE_QUALITY);
            tester.addMappedFragment(0, position, true, "100M", DEFAULT_BASE_QUALITY);
            tester.addMappedFragment(0, position, true, "100M", DEFAULT_BASE_QUALITY);
        }
        tester.runTest();
    }

    @Test
    public void testStackOverFlowPairSetSwap() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();

        final File input = new File("testdata/picard/sam/MarkDuplicates/markDuplicatesWithMateCigar.pairSet.swap.sam");
        final SamReader reader = SamReaderFactory.makeDefault().open(input);
        tester.setHeader(reader.getFileHeader());
        for (final SAMRecord record : reader) {
            tester.addRecord(record);
        }
        CloserUtil.close(reader);
        tester.setExpectedOpticalDuplicate(1);
        tester.runTest();
    }
    
    @Test
    public void testSecondEndIsBeforeFirstInCoordinate() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.addMappedPair(0, 108855339, 108855323, false, false, "33S35M", "17S51M", true, true, false, DEFAULT_BASE_QUALITY); // +/-
        tester.runTest();
    }

    @Test
    public void testPathologicalOrderingAtTheSamePosition() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();

        tester.setExpectedOpticalDuplicate(1);

        tester.addMatePair("RUNID:3:1:15013:113051", 0, 129384554, 129384554, false, false, false, false, "68M", "68M", false, false, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMatePair("RUNID:3:1:15029:113060", 0, 129384554, 129384554, false, false, true, true, "68M", "68M", false, false, false, false, false, DEFAULT_BASE_QUALITY);

        // Create the pathology
        final CloseableIterator<SAMRecord> iterator = tester.getRecordIterator();
        final int[] qualityOffset = {20, 30, 10, 40}; // creates an interesting pathological ordering
        int index = 0;
        while (iterator.hasNext()) {
            final SAMRecord record = iterator.next();
            final byte[] quals = new byte[record.getReadLength()];
            for (int i = 0; i < record.getReadLength(); i++) {
                quals[i] = (byte)(qualityOffset[index] + 10);
            }
            record.setBaseQualities(quals);
            index++;
        }
        iterator.close();

        // Run the test
        tester.runTest();
    }

    @Test
    public void testDifferentChromosomesInOppositeOrder() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.setExpectedOpticalDuplicate(1);
        tester.addMatePair("RUNID:6:101:17642:6835", 0, 1, 123989, 18281, false, false, true, true, "37S64M", "52M49S", false, false, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMatePair("RUNID:6:101:17616:6888", 1, 0, 18281, 123989, false, false, false, false, "52M49S", "37S64M", false, false, false, false, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test
    public void testOpticalDuplicateClustersAddingSecondEndFirstSameCoordinate() {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.setExpectedOpticalDuplicate(1);
        tester.addMatePair("RUNID:1:1:15993:13361", 2, 41212324, 41212310, false, false, false, false, "33S35M", "19S49M", true, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMatePair("RUNID:1:1:16020:13352", 2, 41212324, 41212319, false, false, true, true, "33S35M", "28S40M", true, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @DataProvider(name = "secondarySupplementaryData")
    public Object[][] secondarySupplementaryData() {
        return new Object[][] {
                { true,  true , true},
                { true,  false, true},
                { false, true , true},
                { true,  true , false},
                { true,  false, false},
                { false, true , false}
        };
    }

    @Test(dataProvider = "secondarySupplementaryData")
    public void testTwoMappedPairsWithSupplementaryReads(final Boolean additionalFragSecondary, final Boolean additionalFragSupplementary, final Boolean fragLikeFirst) {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.setExpectedOpticalDuplicate(1);
        tester.addMatePair("RUNID:1:1:15993:13361", 2, 41212324, 41212310, false, false, false, false, "33S35M", "19S49M", true, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMatePair("RUNID:1:1:16020:13352", 2, 41212324, 41212319, false, false, true, true, "33S35M", "28S40M", true, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedFragment(fragLikeFirst ? "RUNID:1:1:15993:13361" : "RUNID:1:1:16020:13352", 1, 400, markSecondaryAndSupplementaryRecordsLikeTheCanonical() && !fragLikeFirst, null, null, additionalFragSecondary, additionalFragSupplementary, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }

    @Test(dataProvider = "secondarySupplementaryData")
    public void testTwoMappedPairsWithSupplementaryReadsAfterCanonical(final Boolean additionalFragSecondary, final Boolean additionalFragSupplementary, final Boolean fragLikeFirst) {
        final AbstractMarkDuplicatesCommandLineProgramTester tester = getTester();
        tester.setExpectedOpticalDuplicate(1);
        tester.addMatePair("RUNID:1:1:15993:13361", 2, 41212324, 41212310, false, false, false, false, "33S35M", "19S49M", true, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMatePair("RUNID:1:1:16020:13352", 2, 41212324, 41212319, false, false, true, true, "33S35M", "28S40M", true, true, false, false, false, DEFAULT_BASE_QUALITY);
        tester.addMappedFragment(fragLikeFirst ? "RUNID:1:1:15993:13361" : "RUNID:1:1:16020:13352", 1, 400, markSecondaryAndSupplementaryRecordsLikeTheCanonical() && !fragLikeFirst, null, null, additionalFragSecondary, additionalFragSupplementary, DEFAULT_BASE_QUALITY);
        tester.runTest();
    }
}
