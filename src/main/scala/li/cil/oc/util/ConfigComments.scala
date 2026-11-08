package li.cil.oc.util

import com.typesafe.config.Config
import com.typesafe.config.ConfigValue

/**
 * Sets the comments rendered above a config value.
 *
 * This used to reach into Typesafe Config's internals (a class placed in
 * com.typesafe.config.impl), which isn't possible now that the library is a
 * separate module. The public origin API does the same since Config 1.3.
 */
object ConfigComments {
  def setComments(config: Config, path: String, comments: java.util.List[String]): Config =
    config.withValue(path, setComments(config.getValue(path), comments))

  def setComments(value: ConfigValue, comments: java.util.List[String]): ConfigValue =
    value.withOrigin(value.origin.withComments(comments))
}
