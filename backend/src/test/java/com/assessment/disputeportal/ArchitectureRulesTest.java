package com.assessment.disputeportal;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@AnalyzeClasses(
    packages = "com.assessment.disputeportal",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureRulesTest {
  @ArchTest
  static final ArchRule controllersDoNotDependOnRepositories =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..");

  @ArchTest
  static final ArchRule externalClassesDoNotDependOnAccountPersistence =
      noExternalPersistenceAccess("account");

  @ArchTest
  static final ArchRule externalClassesDoNotDependOnAuthPersistence =
      noExternalPersistenceAccess("auth");

  @ArchTest
  static final ArchRule externalClassesDoNotDependOnDisputePersistence =
      noExternalPersistenceAccess("dispute");

  @ArchTest
  static final ArchRule externalClassesDoNotDependOnTransactionPersistence =
      noExternalPersistenceAccess("transaction");

  @ArchTest
  static final ArchRule businessFeaturePackagesAreAcyclic =
      slices()
          .matching("com.assessment.disputeportal.(account|auth|dispute|transaction)..")
          .should()
          .beFreeOfCycles();

  @ArchTest
  static final ArchRule dtoPackagesContainOnlyRecords =
      classes()
          .that()
          .resideInAPackage("..model.dto..")
          .should(
              new ArchCondition<>("be immutable DTO records") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                  boolean isRecord = item.reflect().isRecord();
                  String name = item.getSimpleName();
                  boolean hasDtoName =
                      name.endsWith("Request")
                          || name.endsWith("Response")
                          || name.equals("CustomerDecision")
                          || name.equals("AccountSummary")
                          || name.equals("DisputeEligibility");
                  if (!isRecord || !hasDtoName) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item, item.getName() + " is not a request/response DTO record"));
                  }
                }
              });

  @ArchTest
  static final ArchRule transactionalClassesAreNotFinal =
      classes()
          .should(
              new ArchCondition<>("not be final when transactionally proxied") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                  boolean transactionalClass = item.isAnnotatedWith(Transactional.class);
                  boolean transactionalMethod =
                      item.getMethods().stream()
                          .anyMatch(method -> method.isAnnotatedWith(Transactional.class));
                  if (item.getModifiers().contains(JavaModifier.FINAL)
                      && (transactionalClass || transactionalMethod)) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item, item.getName() + " is final but uses @Transactional"));
                  }
                }
              });

  private static ArchRule noExternalPersistenceAccess(String feature) {
    return noClasses()
        .that()
        .resideOutsideOfPackage(".." + feature + "..")
        .and()
        .areNotAnnotatedWith(Configuration.class)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(".." + feature + ".repository..", ".." + feature + ".model.entity..");
  }
}
