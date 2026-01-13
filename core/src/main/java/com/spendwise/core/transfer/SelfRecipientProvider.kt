
package com.spendwise.core.transfer

typealias SelfRecipientProvider = suspend () -> Set<String>
