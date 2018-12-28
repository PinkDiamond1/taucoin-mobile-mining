package org.ethereum.jsontestsuite;

import org.ethereum.di.modules.TestEthereumModule;
import org.ethereum.di.components.DaggerTestEthereumComponent;
import org.ethereum.facade.Ethereum;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

import static org.ethereum.config.SystemProperties.CONFIG;

@Ignore
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GitHubBlockTest {

    //SHACOMMIT of tested commit, ethereum/tests.git
    public String shacommit = "bd07ab22cdc811bd553ef199a8585a0eb1862b28";

    @Before
    public void setup() {
        DaggerTestEthereumComponent.builder()
                .testEthereumModule(new TestEthereumModule())
                .build();
    }

    @Ignore
    @Test
    public void runSingleTest() throws ParseException, IOException {
        CONFIG.setGenesisInfo("frontier.json");

        String shacommit = "24ca9b5f195ccfa38d1bbcc7795529e2569ab5e6";
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcValidBlockTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonSingleBlockTest(json, "RecallSuicidedContractInOneBlock");
    }



    @Test
    public void runBCInvalidHeaderTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcInvalidHeaderTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json,excluded);
    }


    @Test
    public void runBCInvalidRLPTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcInvalidRLPTest.json", shacommit);
        excluded.add("BLOCK_gasLimit_GivenAsList");
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }

    @Ignore
    @Test
    public void runBCRPCAPITest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcRPC_API_Test.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }


    @Test
    public void runBCUncleHeaderValidityTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcUncleHeaderValiditiy.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }

    @Ignore
    @Test
     public void runBCUncleTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcUncleTest.json", shacommit);
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }

    @Ignore
    @Test
    public void runBCValidBlockTest() throws ParseException, IOException {

        CONFIG.setGenesisInfo("frontier.json");

        String shacommit = "24ca9b5f195ccfa38d1bbcc7795529e2569ab5e6";
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcValidBlockTest.json", shacommit);
        Set<String> excluded = new HashSet<>();
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }

    @Test
    public void runBCBlockGasLimitTest() throws ParseException, IOException {
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcBlockGasLimitTest.json", shacommit);
        Set<String> excluded = new HashSet<>();
        excluded.add("SuicideTransaction");
        excluded.add("GasUsedHigherThanBlockGasLimitButNotWithRefundsSuicideFirst");
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }

    @Ignore
    @Test
    public void runBCBruncleTest() throws ParseException, IOException {
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcBruncleTest.json", shacommit);
        Set<String> excluded = new HashSet<>();
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }

    @Ignore
    @Test
    public void runBCForkBlockTest() throws ParseException, IOException {
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcForkBlockTest.json", shacommit);
        Set<String> excluded = new HashSet<>();
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }

    @Test
    public void runBCGasPricerTest() throws ParseException, IOException {
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcGasPricerTest.json", shacommit);
        Set<String> excluded = new HashSet<>();
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }

    @Test
    public void runBCTotalDifficultyTest() throws ParseException, IOException {
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcTotalDifficultyTest.json", shacommit);
        Set<String> excluded = new HashSet<>();
        excluded.add("uncleBlockAtBlock3AfterBlock3");
        excluded.add("lotsOfLeafs");
        excluded.add("sideChainWithMoreTransactions");
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }

    @Ignore
    @Test
    public void runBCWalletTest() throws ParseException, IOException {
        String json = JSONReader.loadJSONFromCommit("BlockchainTests/bcWalletTest.json", shacommit);
        Set<String> excluded = new HashSet<>();
        GitHubJSONTestSuite.runGitHubJsonBlockTest(json, excluded);
    }


}
