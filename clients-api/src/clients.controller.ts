import { Controller, Get, HttpException, HttpStatus, Param } from '@nestjs/common';

type Client = {
  clientId: string;
  name: string;
  segment: 'MAYORISTA' | 'MINORISTA';
  taxRegime: 'RESPONSABLE_IVA' | 'NO_RESPONSABLE';
  region: string;
  channel: string;
};

const clients: Record<string, Client> = {
  'client-001': { clientId: 'client-001', name: 'Distribuidora Norte', segment: 'MAYORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'NORTE', channel: 'TRADICIONAL' },
  'client-002': { clientId: 'client-002', name: 'Mercado Central', segment: 'MINORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'CENTRO', channel: 'MODERNO' },
  'client-003': { clientId: 'client-003', name: 'Bodega San José', segment: 'MINORISTA', taxRegime: 'NO_RESPONSABLE', region: 'SUR', channel: 'TRADICIONAL' },
  'client-004': { clientId: 'client-004', name: 'Mayorista Andino', segment: 'MAYORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'ANDINA', channel: 'DISTRIBUIDOR' },
  'client-005': { clientId: 'client-005', name: 'Autoservicio Pacífico', segment: 'MINORISTA', taxRegime: 'NO_RESPONSABLE', region: 'COSTA', channel: 'MODERNO' }
};

@Controller()
export class ClientsController {
  @Get('health')
  health(): { status: string } {
    return { status: 'UP' };
  }

  @Get('clients/:id')
  findById(@Param('id') id: string): Client {
    const client = clients[id];
    if (!client) {
      throw new HttpException({ message: 'client not found' }, HttpStatus.NOT_FOUND);
    }
    return client;
  }
}
