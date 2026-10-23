import * as StatusBadge from '@/components/ui/status-badge';
import type { AppointmentStatus, SlotStatus } from '@/lib/api/types';
import { APPOINTMENT_STATUS_LABELS, SLOT_STATUS_LABELS } from '@/lib/formatters';

type BadgeStatus = 'completed' | 'pending' | 'failed' | 'disabled';

const APPOINTMENT_BADGE: Record<AppointmentStatus, BadgeStatus> = {
  BOOKED: 'pending',
  ATTENDED: 'completed',
  CANCELLED: 'failed',
  NO_SHOW: 'disabled',
};

interface AppointmentStatusBadgeProps {
  status: AppointmentStatus;
  variant?: 'light' | 'stroke';
}

/** Estado de una cita con los colores de StatusBadge (status-badge-demo). */
export function AppointmentStatusBadge({ status, variant = 'light' }: AppointmentStatusBadgeProps) {
  return (
    <StatusBadge.Root status={APPOINTMENT_BADGE[status]} variant={variant}>
      <StatusBadge.Dot />
      {APPOINTMENT_STATUS_LABELS[status]}
    </StatusBadge.Root>
  );
}

const SLOT_BADGE: Record<SlotStatus, BadgeStatus> = {
  FREE: 'completed',
  BOOKED: 'pending',
  BLOCKED: 'disabled',
};

interface SlotStatusBadgeProps {
  status: SlotStatus;
  variant?: 'light' | 'stroke';
}

/** Estado de un cupo de agenda (módulo de horarios). */
export function SlotStatusBadge({ status, variant = 'light' }: SlotStatusBadgeProps) {
  return (
    <StatusBadge.Root status={SLOT_BADGE[status]} variant={variant}>
      <StatusBadge.Dot />
      {SLOT_STATUS_LABELS[status]}
    </StatusBadge.Root>
  );
}
