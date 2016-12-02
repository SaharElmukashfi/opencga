/*
 * Copyright 2015-2016 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.storage.core.alignment.local;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by imedina on 01/11/16.
 */
public class LocalAlignmentStorageETLTest {
    @Test
    public void transform() throws Exception {
        Path inputPath = Paths.get("/home/jtarraga/data150/bam/NA12877_chrM.bam");
//        Path inputPath = Paths.get(getClass().getResource("/HG00096.chrom20.small.bam").toURI());
        Path coverageDBPath = inputPath.getParent().resolve(CoverageDBManager.COVERAGE_DATABASE_NAME);

        System.out.println("inputPath = " + inputPath);
        System.out.println("coverageDBPath = " + coverageDBPath);
        coverageDBPath.toFile().delete();

//        String inputPath = "/tmp/kk/ebi.bam";
//        String inputPath = "/tmp/kk/HG00096.chrom20.small.bam";
        LocalAlignmentStorageETL storageETL = new LocalAlignmentStorageETL();
        storageETL.transform(inputPath.toUri(), null, inputPath.getParent().toUri());
    }

    @Test
    public void meanCoverage() {
//        DefaultAlignmentDBAdaptor adaptor = new DefaultAlignmentDBAdaptor();
//
//        String inputPath = "/tmp/kk/ebi.bam";
////        String inputPath = "/tmp/kk/HG00096.chrom20.small.bam";
//        String outputPath = "/tmp/kk/";
//        Query query = new Query();
////        query.put("region", "20:1-70000000");
//        query.put("region", "gi|30407139|emb|AL111168.1|:1-70000000");
//        QueryOptions queryOptions = new QueryOptions();
//        queryOptions.put("contained", false);
//
//        try {
//            QueryResult<RegionCoverage> res = adaptor.coverage(Paths.get(inputPath), Paths.get(outputPath),
//                    query, queryOptions);
//            System.out.println(res.getResult().get(0).toJSON());
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
}