package edu.ums.testkit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import edu.ums.platform.invoke.Interceptor;
import java.util.List;

/**
 * Architecture rules shared by every module (CLAUDE.md §2–§3, P0 Step 12). Rules allow an empty
 * match so they hold before the feature packages exist. Each module applies {@link #ALL} in its
 * {@code ArchitectureTest} against production classes only.
 */
public final class ArchitectureRules {

  /** {@code ums-api} depends on nothing but itself and the JDK. */
  public static final ArchRule API_DEPENDS_ON_NOTHING =
      classes()
          .that()
          .resideInAPackage("edu.ums.api..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("edu.ums.api..", "java..")
          .allowEmptyShould(true)
          .because("the frontend depends only on ums-api, which depends on nothing");

  /**
   * Call direction facade → service → repository: repositories never call services or facades;
   * domain classes never call repositories, services or facades.
   */
  public static final ArchRule LAYER_DIRECTION =
      CompositeArchRule.of(
              noClasses()
                  .that()
                  .resideInAPackage("..repository..")
                  .should()
                  .dependOnClassesThat()
                  .resideInAnyPackage("..service..", "..facade..")
                  .allowEmptyShould(true))
          .and(
              noClasses()
                  .that()
                  .resideInAPackage("..domain..")
                  .should()
                  .dependOnClassesThat()
                  .resideInAnyPackage("..repository..", "..service..", "..facade..")
                  .allowEmptyShould(true))
          .because("calls flow facade -> interceptors -> service -> repository -> DB");

  /**
   * {@code java.sql} only in repository packages and {@code edu.ums.platform.db}. Exemptions: the
   * testkit (test infrastructure) and {@code SqlErrorMapper}, which maps {@code SQLException} by
   * design (master prompt §10, P0 Step 5).
   */
  public static final ArchRule JDBC_ONLY_IN_DATA_ACCESS =
      noClasses()
          .that()
          .resideOutsideOfPackages("..repository..", "edu.ums.platform.db..", "edu.ums.testkit..")
          .and()
          .doNotHaveFullyQualifiedName("edu.ums.platform.error.SqlErrorMapper")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("java.sql..")
          .allowEmptyShould(true)
          .because("JDBC belongs to repositories and platform.db");

  /** No mutable static state: static fields must be final (constants and loggers). */
  public static final ArchRule NO_MUTABLE_STATIC_FIELDS =
      fields()
          .that()
          .areStatic()
          .should()
          .beFinal()
          .allowEmptyShould(true)
          .because("shared mutable state lives in concurrent structures owned by instances");

  /** No dependency cycles between {@code edu.ums.core.*} feature packages. */
  public static final ArchRule NO_FEATURE_CYCLES =
      slices()
          .matching("edu.ums.core.(*)..")
          .should()
          .beFreeOfCycles()
          .allowEmptyShould(true)
          .because("features must stay independently changeable");

  /** Services never depend on facades (services call services through interfaces). */
  public static final ArchRule SERVICES_DO_NOT_DEPEND_ON_FACADES =
      noClasses()
          .that()
          .resideInAPackage("..service..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..facade..")
          .allowEmptyShould(true)
          .because("facades are the outer boundary; services must not call back into them");

  /** Interceptors live only in {@code edu.ums.platform.invoke}. */
  public static final ArchRule INTERCEPTORS_ONLY_IN_INVOKE =
      classes()
          .that()
          .implement(Interceptor.class)
          .should()
          .resideInAPackage("edu.ums.platform.invoke..")
          .allowEmptyShould(true)
          .because("the interceptor chain has fixed slots owned by the platform");

  /** {@code *Repository} interfaces live in {@code repository} packages. */
  public static final ArchRule REPOSITORY_INTERFACES_IN_REPOSITORY_PACKAGES =
      classes()
          .that()
          .areInterfaces()
          .and()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .resideInAPackage("..repository..")
          .allowEmptyShould(true)
          .because("repositories are the only data-access layer");

  /** Every rule, in a stable order. */
  public static final List<ArchRule> ALL =
      List.of(
          API_DEPENDS_ON_NOTHING,
          LAYER_DIRECTION,
          JDBC_ONLY_IN_DATA_ACCESS,
          NO_MUTABLE_STATIC_FIELDS,
          NO_FEATURE_CYCLES,
          SERVICES_DO_NOT_DEPEND_ON_FACADES,
          INTERCEPTORS_ONLY_IN_INVOKE,
          REPOSITORY_INTERFACES_IN_REPOSITORY_PACKAGES);

  private ArchitectureRules() {}

  /** Imports the production classes of every {@code edu.ums} module on the classpath. */
  public static JavaClasses productionClasses() {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("edu.ums");
  }
}
