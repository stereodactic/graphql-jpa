package org.crygier.graphql

import graphql.ExecutionInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import spock.lang.Specification;

@Configuration
@SpringBootTest(classes = TestApplication)
public class SecurityTest extends Specification {

    @Autowired
    private GraphQLExecutor executor;

    def 'queries TestOneSecuredParentEntity without a SecurityId'() {
        given:
        def query = '''
        {
            TestOneSecuredParentEntity {
                id
                name
            }
        }
        '''

        def expected = [
                TestOneSecuredParentEntity: null
        ]

        when:
        def result = executor.execute(query).data

        then:
        result == expected
    }

    def 'queries TestOneSecuredParentEntity with a SecurityId'() {
        given:
        def query = '''
        {
            TestOneSecuredParentEntity {
                id
                name
            }
        }
        '''

        def executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .build()

        def graphQLContext = executionInput.getGraphQLContext()
        graphQLContext.put("securityId", 1L)

        def expected = [
                TestOneSecuredParentEntity: [
                        [id: 1, name: 'Tom'],
                        [id: 2, name: 'Dick']
                ]
        ]

        when:
        def result = executor.execute(executionInput).data

        then:
        result == expected
    }

    def 'queries TestOneSecuredChildEntity with a SecurityId'() {
        given:
        def query = '''
        {
            TestOneSecuredParentEntity {
                id
                name
                children {
                    id
                    name
                }
            }
        }
        '''

        def executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .build()

        def graphQLContext = executionInput.getGraphQLContext()
        graphQLContext.put("securityId", 2L)

        def expected = [
                TestOneSecuredParentEntity: [
                        [id: 3, name: 'Harry', children: [ [id: 1, name: 'John'], [id: 2, name: 'Jane'] ]]
                ]
        ]

        when:
        def result = executor.execute(executionInput).data

        then:
        result == expected
    }

    def 'queries TestTwoInsecureParentEntity without a SecurityId'() {
        given:
        def query = '''
        {
            TestTwoInsecureParentEntity {
                id
                name
                children {
                    id
                    name
                }
            }
        }
        '''

        def expected = [
                TestTwoInsecureParentEntity: [
                        [id: 1, name: 'Tom', children: null],
                        [id: 2, name: 'Dick', children: null],
                        [id: 3, name: 'Harry', children: null]
                ]
        ]

        when:
        def result = executor.execute(query).data

        then:
        result == expected
    }

    def 'queries TestTwoSecuredChildEntity with a SecurityId'() {
        given:
        def query = '''
        {
            TestTwoInsecureParentEntity {
                id
                name
                children {
                    id
                    name
                }
            }
        }
        '''

        def executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .build()

        def graphQLContext = executionInput.getGraphQLContext()
        graphQLContext.put("securityId", 2L)

        def expected = [
                TestTwoInsecureParentEntity: [
                        [id: 1, name: 'Tom', children: []],
                        [id: 2, name: 'Dick', children: []],
                        [id: 3, name: 'Harry', children: [ [id: 1, name: 'John'], [id: 2, name: 'Jane'] ]]
                ]
        ]

        when:
        def result = executor.execute(executionInput).data

        then:
        result == expected
    }

}
