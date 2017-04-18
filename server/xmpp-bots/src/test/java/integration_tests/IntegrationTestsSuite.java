package integration_tests;

import integration_tests.tests.*;
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
