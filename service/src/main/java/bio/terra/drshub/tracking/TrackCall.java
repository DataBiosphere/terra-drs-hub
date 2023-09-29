package bio.terra.drshub.tracking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Add this annotation to a method to track its call as a user event in Bard. Note this should just
 * be used within Controller classes since these represent the explicit API calls that users are
 * making. Tracking internal calls is not useful and likely too noisy.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackCall {}
