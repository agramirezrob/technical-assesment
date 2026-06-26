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
  'CLI-99821': { clientId: 'CLI-99821', name: 'Distribuidora Andina S.A.S', segment: 'MAYORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'Valle del Cauca', channel: 'B2B' },
  'CLI-99822': { clientId: 'CLI-99822', name: 'Mercado Central', segment: 'MINORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'Bogota D.C.', channel: 'MODERNO' },
  'CLI-99823': { clientId: 'CLI-99823', name: 'Bodega San Jose', segment: 'MINORISTA', taxRegime: 'NO_RESPONSABLE', region: 'Antioquia', channel: 'TRADICIONAL' },
  'CLI-99824': { clientId: 'CLI-99824', name: 'Mayorista Caribe', segment: 'MAYORISTA', taxRegime: 'RESPONSABLE_IVA', region: 'Atlantico', channel: 'DISTRIBUIDOR' },
  'CLI-99825': { clientId: 'CLI-99825', name: 'Autoservicio Pacifico', segment: 'MINORISTA', taxRegime: 'NO_RESPONSABLE', region: 'Cauca', channel: 'MODERNO' }
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
