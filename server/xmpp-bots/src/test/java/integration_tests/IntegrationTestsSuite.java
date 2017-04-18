package integration_tests;

import integration_tests.tests.ClientAdminTest;
import integration_tests.tests.ClientCancelTest;
import integration_tests.tests.ClientExpertTest;
import integration_tests.tests.ExpertCancelsTest;
import integration_tests.tests.ExpertsAssignmentTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * User: Artem
 * Date: 22.02.2017
 * Time: 19:51
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({ClientAdminTest.class, ClientExpertTest.class, ClientCancelTest.class, ExpertCancelsTest.class, ExpertsAssignmentTest.class})
public class IntegrationTestsSuite {
}
