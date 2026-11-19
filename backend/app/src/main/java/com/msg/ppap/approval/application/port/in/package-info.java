/**
 * Driving ports (use case interfaces) of the Approval module. Exposed as named interface {@code ports} to the
 * {@code exchange} module, which delivers inbound partner messages ({@code Receive*UseCase}) and asks for the payload
 * of outbound messages ({@link com.msg.ppap.approval.application.port.in.OutboundMessageUseCase}).
 */
@PrimaryPort
@NamedInterface("ports")
package com.msg.ppap.approval.application.port.in;

import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.modulith.NamedInterface;
